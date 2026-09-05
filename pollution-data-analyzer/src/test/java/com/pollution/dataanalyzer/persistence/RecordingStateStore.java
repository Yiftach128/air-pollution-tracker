package com.pollution.dataanalyzer.persistence;

import com.pollution.dataanalyzer.entities.RollingAverageState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An {@link IRollingAverageStateStore} for the analyzer's tests: hands out
 * whatever states the test says it holds, remembers every save, and can be
 * told to fail either way.
 */
public final class RecordingStateStore implements IRollingAverageStateStore {

    private final List<RollingAverageState> held = new ArrayList<>();
    private final List<RollingAverageState> saved = new ArrayList<>();
    private RuntimeException loadFailure;
    private RuntimeException saveFailure;
    private boolean closed;

    /** What {@link #loadAll()} returns, as if persisted by an earlier run. */
    public synchronized void holds(RollingAverageState... states) {
        held.addAll(List.of(states));
    }

    @Override
    public synchronized void save(RollingAverageState state) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        saved.add(state);
    }

    @Override
    public synchronized Collection<RollingAverageState> loadAll() {
        if (loadFailure != null) {
            throw loadFailure;
        }
        return List.copyOf(held);
    }

    /** Every save, in order. */
    public synchronized List<RollingAverageState> saved() {
        return List.copyOf(saved);
    }

    public synchronized void failLoadsWith(RuntimeException failure) {
        this.loadFailure = failure;
    }

    public synchronized void failSavesWith(RuntimeException failure) {
        this.saveFailure = failure;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
