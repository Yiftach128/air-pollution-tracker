package com.pollution.datawriter;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.ILatestReadingStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link ILatestReadingStore} for the writer's tests: keeps each series'
 * newest reading in a map with the real store's rule (never backwards),
 * remembers every save, and can be told to fail.
 */
final class RecordingLatestReadingStore implements ILatestReadingStore {

    private final Map<String, PollutionData> current = new LinkedHashMap<>();
    private final List<PollutionData> saved = new ArrayList<>();
    private RuntimeException failure;
    private boolean closed;

    @Override
    public synchronized boolean save(PollutionData reading) {
        if (failure != null) {
            throw failure;
        }
        saved.add(reading);
        String key = keyOf(reading.source(), reading.pollutant());
        PollutionData stored = current.get(key);
        if (stored != null && stored.timestamp().isAfter(reading.timestamp())) {
            return false;
        }
        current.put(key, reading);
        return true;
    }

    @Override
    public synchronized Optional<PollutionData> find(String source, Pollutant pollutant) {
        return Optional.ofNullable(current.get(keyOf(source, pollutant)));
    }

    @Override
    public synchronized List<PollutionData> findAll() {
        return List.copyOf(current.values());
    }

    /** Every save attempted, in order, whether the reading became current or not. */
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
    public synchronized void close() {
        closed = true;
    }

    private static String keyOf(String source, Pollutant pollutant) {
        return source + ":" + pollutant.name();
    }
}
