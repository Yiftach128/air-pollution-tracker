package com.pollution.persistence;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import java.util.List;
import java.util.Optional;

/**
 * The current reading of every series the system follows — one source's
 * newest reading of one pollutant — kept for fast "what is the air like
 * now" lookups, next to the full history in {@link IPollutionRepository}.
 * It is written by the service that stores readings and read by the
 * services that show them, so it lives here, with the other shared
 * persistence abstractions, rather than inside one service.
 * <p>
 * A reading counts as current only for a limited time after it was saved,
 * so a series that stops reporting disappears from the store on its own
 * instead of showing a stale value forever.
 * <p>
 * Every method throws {@link PollutionCacheException} if the store cannot be
 * reached; callers decide whether that is fatal.
 */
public interface ILatestReadingStore extends AutoCloseable {

    /**
     * Makes the reading its series' current one, unless the one stored is
     * newer — a redelivered old message never moves a series backwards.
     *
     * @return {@code true} if the reading is now the series' current one,
     *         {@code false} if a newer one was kept
     */
    boolean save(PollutionData reading);

    /** The current reading of one series, or empty if it has none (never reported, or expired). */
    Optional<PollutionData> find(String source, Pollutant pollutant);

    /** The current reading of every series that has one; empty when none does. */
    List<PollutionData> findAll();

    @Override
    void close();
}
