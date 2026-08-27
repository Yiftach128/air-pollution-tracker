package com.pollution.dataanalyzer.persistence;

import com.pollution.dataanalyzer.entities.RollingAverageState;
import java.util.Collection;

/**
 * Durable storage for rolling-average state, one entry per series (sensor and
 * pollutant) holding that series' longest window, so the analyzer can pick up
 * where it left off after a restart.
 */
public interface IRollingAverageStateStore extends AutoCloseable {

    /**
     * Upserts the state under its series, replacing whatever was stored for
     * that series. Other series are left untouched.
     */
    void save(RollingAverageState state);

    /**
     * Every stored state; empty when nothing is stored.
     *
     * @throws RuntimeException if the store cannot be reached: callers decide
     *                          whether to start without restored state
     */
    Collection<RollingAverageState> loadAll();

    @Override
    void close();
}
