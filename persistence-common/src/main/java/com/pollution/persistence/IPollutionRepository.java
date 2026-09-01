package com.pollution.persistence;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.entities.BucketAverage;
import com.pollution.persistence.entities.ReadingsSummary;
import com.pollution.persistence.entities.SourceSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Durable storage of the pollution readings the system collects, kept so
 * they can be queried later as historic data. A reading is identified by
 * its source, pollutant and timestamp; storing one that is already stored
 * is a no-op, so a redelivered message never produces a second copy.
 * <p>
 * Besides the readings themselves the repository answers the aggregate
 * questions a history view asks — which sources there are, what a range of
 * readings amounts to, what they average over fixed slices of time — so
 * that the aggregation happens where the data is instead of in a service.
 * Every range is {@code [from, to)}: {@code from} inclusive, {@code to}
 * exclusive and not before {@code from}.
 * <p>
 * Every method throws {@link PollutionRepositoryException} if the store
 * cannot be reached; callers decide whether that is fatal.
 */
public interface IPollutionRepository extends AutoCloseable {

    /**
     * Stores the reading, unless one with the same source, pollutant and
     * timestamp is already stored.
     *
     * @return {@code true} if the reading was stored now, {@code false} if it
     *         was already there
     */
    boolean save(PollutionData reading);

    /**
     * The stored readings of one series — one source's readings of one
     * pollutant — taken in a time range, oldest first.
     *
     * @return the readings in ascending timestamp order; empty if there are none
     */
    List<PollutionData> findReadings(String source, Pollutant pollutant, Instant from, Instant to);

    /**
     * The stored readings of every pollutant of one source taken in a time
     * range, oldest first; readings taken at the same time are ordered by
     * pollutant.
     *
     * @return the readings in ascending timestamp order; empty if there are none
     */
    List<PollutionData> findReadings(String source, Instant from, Instant to);

    /**
     * Every source that has readings stored, with the city and time of its
     * newest one, ordered by source.
     */
    List<SourceSummary> findSources();

    /**
     * What one source's readings in a time range amount to, one summary per
     * pollutant that has readings in the range, ordered by pollutant.
     *
     * @return the summaries; empty if the source has no readings in the range
     */
    List<ReadingsSummary> summarize(String source, Instant from, Instant to);

    /**
     * One source's readings in a time range averaged over consecutive
     * buckets of a fixed length, one average per pollutant per bucket that
     * has readings, ordered by bucket start and then pollutant. Buckets are
     * aligned to the epoch (an hour-long bucket starts on the hour, a
     * day-long one at 00:00 UTC), not to {@code from}.
     *
     * @param bucket the length of every bucket; positive
     * @return the averages; empty if the source has no readings in the range
     */
    List<BucketAverage> findAverages(String source, Instant from, Instant to, Duration bucket);

    @Override
    void close();
}
