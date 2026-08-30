package com.pollution.persistence.postgres;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.PollutionRepositoryException;
import com.pollution.persistence.postgres.entities.PollutionDataEntity;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
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
 * skipped rather than reported.
 */
public class PostgresPollutionRepository implements IPollutionRepository {

    private static final Logger logger = PollutionLogger.getLogger(PostgresPollutionRepository.class);

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
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to " + to + " is before from " + from);
        }
        try (Session session = sessionFactory.openSession()) {
            session.setDefaultReadOnly(true);
            return session.createSelectionQuery(
                            "from PollutionDataEntity"
                                    + " where source = :source and pollutant = :pollutant"
                                    + " and measuredAt >= :from and measuredAt < :to"
                                    + " order by measuredAt",
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
                    "failed to find readings of " + source + " " + pollutant + " in [" + from + ", " + to + ")", e);
        }
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
