package com.pollution.dataanalyzer.persistence;

import com.pollution.dataanalyzer.entities.RollingAverageSnapshot;
import java.util.Collection;

/**
 * Durable storage for rolling-average snapshots, one per sensor, so the
 * analyzer can pick up where it left off after a restart.
 */
public interface ISnapshotStore extends AutoCloseable {

    /**
     * Upserts each snapshot under its sensor id. Sensors absent from the
     * collection are left untouched.
     */
    void saveAll(Collection<RollingAverageSnapshot> snapshots);

    /**
     * Every stored snapshot; empty when nothing is stored.
     *
     * @throws RuntimeException if the store cannot be reached: callers decide
     *                          whether to start without restored state
     */
    Collection<RollingAverageSnapshot> loadAll();

    @Override
    void close();
}
