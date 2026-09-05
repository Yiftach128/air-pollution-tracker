package com.pollution.datawriter;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.entities.BucketAverage;
import com.pollution.persistence.entities.ReadingsSummary;
import com.pollution.persistence.entities.SourceSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link IPollutionRepository} for the writer's tests: remembers every
 * save, answers {@code false} for a reading saved before (the real one is
 * idempotent), and can be told to fail. The writer never queries, so the
 * queries answer nothing.
 */
final class RecordingRepository implements IPollutionRepository {

    private final List<PollutionData> saved = new ArrayList<>();
    private final Set<PollutionData> stored = new HashSet<>();
    private RuntimeException failure;
    private boolean closed;

    @Override
    public synchronized boolean save(PollutionData reading) {
        if (failure != null) {
            throw failure;
        }
        saved.add(reading);
        return stored.add(reading);
    }

    /** Every save attempted, in order, whether the reading was new or not. */
    synchronized List<PollutionData> saved() {
        return List.copyOf(saved);
    }

    synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public List<PollutionData> findReadings(String source, Pollutant pollutant, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public List<PollutionData> findReadings(String source, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public List<SourceSummary> findSources() {
        return List.of();
    }

    @Override
    public List<ReadingsSummary> summarize(String source, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public List<BucketAverage> findAverages(String source, Instant from, Instant to, Duration bucket) {
        return List.of();
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
