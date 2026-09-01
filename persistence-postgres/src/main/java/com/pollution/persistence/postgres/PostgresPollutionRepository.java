package com.pollution.persistence.postgres;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.PollutionRepositoryException;
import com.pollution.persistence.entities.BucketAverage;
import com.pollution.persistence.entities.ReadingsSummary;
import com.pollution.persistence.entities.SourceSummary;
import com.pollution.persistence.postgres.entities.PollutionDataEntity;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;

/**
 * An {@link IPollutionRepository} that keeps readings in a PostgreSQL table
 * through Hibernate, one {@link PollutionDataEntity} row per reading. Owns
 * the {@link SessionFactory} (and with it the HikariCP connection pool),
 * which is built when the repository is and released by {@link #close()}.
 * The table is created or brought up to date with the entity mapping on
 * startup ({@code hbm2ddl.auto=update}).
 * <p>
 * Every save runs in its own short transaction. A reading that hits the
 * unique constraint of the table is one that is already stored, so it is
 * skipped rather than reported. The queries are read-only sessions; the
 * aggregates are computed by the database, in HQL where HQL can express
 * them and in PostgreSQL's own SQL for the time buckets.
 */
public class PostgresPollutionRepository implements IPollutionRepository {

    private static final Logger logger = PollutionLogger.getLogger(PostgresPollutionRepository.class);

    /**
     * Readings of one source averaged over buckets of {@code :bucketSeconds}
     * seconds, aligned to the epoch: every reading falls in the bucket that
     * starts at the largest multiple of the bucket length not after it.
     * The bucket start comes back as seconds since the epoch, a number,
     * which keeps the row shape independent of how the driver maps
     * timestamps.
     */
    private static final String BUCKET_AVERAGES_SQL =
            "select pollutant,"
                    + " floor(extract(epoch from measured_at) / :bucketSeconds) * :bucketSeconds as bucket_epoch,"
                    + " avg(value), min(value), max(value), count(*)"
                    + " from pollution_data"
                    + " where source = :source and measured_at >= :from and measured_at < :to"
                    + " group by pollutant, bucket_epoch"
                    + " order by bucket_epoch, pollutant";

    private final SessionFactory sessionFactory;

    /**
     * @param jdbcUrl  {@code jdbc:postgresql://host:port/database}
     * @param user     database user
     * @param password the password of that user; empty if none
     * @param poolSize most connections the pool keeps open; must be positive
     * @throws PollutionRepositoryException if the database cannot be reached or the schema cannot be updated
     */
    public PostgresPollutionRepository(String jdbcUrl, String user, String password, int poolSize) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive, was " + poolSize);
        }
        try {
            // HikariCP is picked as the connection provider because hibernate-hikaricp is on the
            // classpath; the dialect is detected from the connection.
            this.sessionFactory = new Configuration()
                    .addAnnotatedClass(PollutionDataEntity.class)
                    .setProperty("hibernate.connection.url", jdbcUrl)
                    .setProperty("hibernate.connection.username", user)
                    .setProperty("hibernate.connection.password", password)
                    .setProperty("hibernate.hikari.maximumPoolSize", String.valueOf(poolSize))
                    .setProperty("hibernate.hbm2ddl.auto", "update")
                    .buildSessionFactory();
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException("failed to connect to postgres at " + jdbcUrl, e);
        }
        logger.info("pollution repository backed by postgres at {} as {}", jdbcUrl, user);
    }

    @Override
    public boolean save(PollutionData reading) {
        Objects.requireNonNull(reading, "reading");
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                session.persist(PollutionDataEntity.fromDomain(reading));
                // flush here so a unique-constraint hit surfaces as a ConstraintViolationException
                // instead of being wrapped in the failure of the commit
                session.flush();
                transaction.commit();
                return true;
            } catch (ConstraintViolationException e) {
                rollback(transaction);
                logger.debug("already stored, skipping {}", reading);
                return false;
            } catch (RuntimeException e) {
                rollback(transaction);
                throw e;
            }
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException("failed to save " + reading, e);
        }
    }

    @Override
    public List<PollutionData> findReadings(String source, Pollutant pollutant, Instant from, Instant to) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        requireRange(from, to);
        try (Session session = openReadOnlySession()) {
            return session.createSelectionQuery(
                            "from PollutionDataEntity e"
                                    + " where e.source = :source and e.pollutant = :pollutant"
                                    + " and e.measuredAt >= :from and e.measuredAt < :to"
                                    + " order by e.measuredAt",
                            PollutionDataEntity.class)
                    .setParameter("source", source)
                    .setParameter("pollutant", pollutant)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList()
                    .stream()
                    .map(PollutionDataEntity::toDomain)
                    .toList();
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException(
                    "failed to find readings of " + source + " " + pollutant + " in " + rangeOf(from, to), e);
        }
    }

    @Override
    public List<PollutionData> findReadings(String source, Instant from, Instant to) {
        Objects.requireNonNull(source, "source");
        requireRange(from, to);
        try (Session session = openReadOnlySession()) {
            return session.createSelectionQuery(
                            "from PollutionDataEntity e"
                                    + " where e.source = :source"
                                    + " and e.measuredAt >= :from and e.measuredAt < :to"
                                    + " order by e.measuredAt, e.pollutant",
                            PollutionDataEntity.class)
                    .setParameter("source", source)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList()
                    .stream()
                    .map(PollutionDataEntity::toDomain)
                    .toList();
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException(
                    "failed to find readings of " + source + " in " + rangeOf(from, to), e);
        }
    }

    @Override
    public List<SourceSummary> findSources() {
        try (Session session = openReadOnlySession()) {
            List<Object[]> rows = session.createSelectionQuery(
                            "select e.source, e.city, max(e.measuredAt) from PollutionDataEntity e"
                                    + " group by e.source, e.city"
                                    + " order by e.source",
                            Object[].class)
                    .getResultList();
            // a source that reported from more than one city appears once per city; keep the
            // city of its newest reading
            Map<String, SourceSummary> newestBySource = new LinkedHashMap<>();
            for (Object[] row : rows) {
                SourceSummary summary = new SourceSummary((String) row[0], (String) row[1], (Instant) row[2]);
                newestBySource.merge(summary.source(), summary,
                        (a, b) -> b.lastReportedAt().isAfter(a.lastReportedAt()) ? b : a);
            }
            return List.copyOf(newestBySource.values());
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException("failed to find the sources", e);
        }
    }

    @Override
    public List<ReadingsSummary> summarize(String source, Instant from, Instant to) {
        Objects.requireNonNull(source, "source");
        requireRange(from, to);
        try (Session session = openReadOnlySession()) {
            List<Object[]> rows = session.createSelectionQuery(
                            "select e.pollutant, avg(e.value), min(e.value), max(e.value), count(*)"
                                    + " from PollutionDataEntity e"
                                    + " where e.source = :source"
                                    + " and e.measuredAt >= :from and e.measuredAt < :to"
                                    + " group by e.pollutant"
                                    + " order by e.pollutant",
                            Object[].class)
                    .setParameter("source", source)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList();
            List<ReadingsSummary> summaries = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                summaries.add(new ReadingsSummary((Pollutant) row[0],
                        toDouble(row[1]), toDouble(row[2]), toDouble(row[3]), toLong(row[4])));
            }
            return List.copyOf(summaries);
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException(
                    "failed to summarize the readings of " + source + " in " + rangeOf(from, to), e);
        }
    }

    @Override
    public List<BucketAverage> findAverages(String source, Instant from, Instant to, Duration bucket) {
        Objects.requireNonNull(source, "source");
        requireRange(from, to);
        Objects.requireNonNull(bucket, "bucket");
        if (bucket.isNegative() || bucket.isZero()) {
            throw new IllegalArgumentException("bucket must be positive, was " + bucket);
        }
        long bucketSeconds = Math.max(1, bucket.toSeconds());
        try (Session session = openReadOnlySession()) {
            List<Object[]> rows = session.createNativeQuery(BUCKET_AVERAGES_SQL, Object[].class)
                    .setParameter("bucketSeconds", bucketSeconds)
                    .setParameter("source", source)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList();
            List<BucketAverage> averages = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                averages.add(new BucketAverage(
                        Pollutant.valueOf((String) row[0]),
                        Instant.ofEpochSecond(toLong(row[1])),
                        toDouble(row[2]), toDouble(row[3]), toDouble(row[4]), toLong(row[5])));
            }
            return List.copyOf(averages);
        } catch (PersistenceException e) {
            throw new PollutionRepositoryException("failed to average the readings of " + source
                    + " in " + rangeOf(from, to) + " over " + bucket + " buckets", e);
        }
    }

    private Session openReadOnlySession() {
        Session session = sessionFactory.openSession();
        session.setDefaultReadOnly(true);
        return session;
    }

    private static void requireRange(Instant from, Instant to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to " + to + " is before from " + from);
        }
    }

    private static String rangeOf(Instant from, Instant to) {
        return "[" + from + ", " + to + ")";
    }

    private static double toDouble(Object number) {
        return ((Number) number).doubleValue();
    }

    private static long toLong(Object number) {
        return ((Number) number).longValue();
    }

    private static void rollback(Transaction transaction) {
        try {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } catch (PersistenceException e) {
            logger.warn("rollback failed", e);
        }
    }

    @Override
    public void close() {
        sessionFactory.close();
    }
}
