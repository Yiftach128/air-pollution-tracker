package com.pollution.persistence;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps each series' current reading in an {@link IPollutionCache} under the
 * key {@code latest-reading:<source>:<POLLUTANT>}. The key layout is owned
 * here, not by any service, so every service that uses the store agrees on
 * it. Every save gives the entry a fresh lifetime of {@code ttl}, so a series
 * that stops reporting is dropped from the cache on its own. Works with any
 * cache implementation; the concrete one is chosen in each service's Wiring.
 * <p>
 * A save reads the stored reading before writing, so two threads saving
 * readings of the same series at once could race; the writer saves readings
 * from a single subscriber thread.
 */
public final class CacheBackedLatestReadingStore implements ILatestReadingStore {

    private static final String KEY_PREFIX = "latest-reading:";

    private final IPollutionCache<PollutionData> cache;
    private final Duration ttl;

    /**
     * @param cache where the readings live
     * @param ttl   how long a saved reading stays current if no newer one replaces it; must be positive
     */
    public CacheBackedLatestReadingStore(IPollutionCache<PollutionData> cache, Duration ttl) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
    }

    @Override
    public boolean save(PollutionData reading) {
        Objects.requireNonNull(reading, "reading");
        String key = keyOf(reading.source(), reading.pollutant());
        Optional<PollutionData> stored = cache.getObjectValue(key);
        if (stored.isPresent() && stored.get().timestamp().isAfter(reading.timestamp())) {
            return false;
        }
        cache.setObjectValue(key, reading, ttl);
        return true;
    }

    @Override
    public Optional<PollutionData> find(String source, Pollutant pollutant) {
        return cache.getObjectValue(keyOf(source, pollutant));
    }

    @Override
    public List<PollutionData> findAll() {
        return cache.getObjectValuesByPattern(KEY_PREFIX + "*");
    }

    @Override
    public void close() {
        cache.close();
    }

    private static String keyOf(String source, Pollutant pollutant) {
        return KEY_PREFIX + Objects.requireNonNull(source, "source") + ":"
                + Objects.requireNonNull(pollutant, "pollutant").name();
    }
}
