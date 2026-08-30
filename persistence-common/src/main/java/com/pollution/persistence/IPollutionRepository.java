package com.pollution.persistence;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import java.time.Instant;
import java.util.List;

/**
 * Durable storage of the pollution readings the system collects, kept so
 * they can be queried later as historic data. A reading is identified by
 * its source, pollutant and timestamp; storing one that is already stored
 * is a no-op, so a redelivered message never produces a second copy.
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
     * @param from start of the range, inclusive
     * @param to   end of the range, exclusive; not before {@code from}
     * @return the readings in ascending timestamp order; empty if there are none
     */
    List<PollutionData> findReadings(String source, Pollutant pollutant, Instant from, Instant to);

    @Override
    void close();
}
