package com.pollution.dataanalyzer.persistence;

import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import com.pollution.persistence.IPollutionCache;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

/**
 * Keeps each series' state in an {@link IPollutionCache} under the key
 * {@code <keyPrefix><sensorId>:<POLLUTANT>}. The prefix is the analyzer's
 * namespace in the shared cache, so loading everything under it never picks
 * up another service's keys. Every save gives the entry a fresh lifetime of
 * {@code stateTtl}, so a series that stops being saved is dropped from the
 * cache on its own. Works with any cache implementation; the concrete one is
 * chosen in Wiring.
 */
public final class CacheBackedRollingAverageStateStore implements IRollingAverageStateStore {

    private final IPollutionCache<RollingAverageState> cache;
    private final String keyPrefix;
    private final Duration stateTtl;

    /**
     * @param cache     where the state lives
     * @param keyPrefix the analyzer's key namespace; must not be blank
     * @param stateTtl  how long a saved state lives before the cache drops it; must be positive
     */
    public CacheBackedRollingAverageStateStore(IPollutionCache<RollingAverageState> cache,
                                               String keyPrefix,
                                               Duration stateTtl) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.stateTtl = Objects.requireNonNull(stateTtl, "stateTtl");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        if (stateTtl.isNegative() || stateTtl.isZero()) {
            throw new IllegalArgumentException("stateTtl must be positive, was " + stateTtl);
        }
    }

    @Override
    public void save(RollingAverageState state) {
        cache.setObjectValue(keyOf(state.sensorPollutant()), state, stateTtl);
    }

    @Override
    public Collection<RollingAverageState> loadAll() {
        return cache.getObjectValuesByPattern(keyPrefix + "*");
    }

    @Override
    public void close() {
        cache.close();
    }

    private String keyOf(SensorPollutant sensorPollutant) {
        return keyPrefix + sensorPollutant.sensorId() + ":" + sensorPollutant.pollutant().name();
    }
}
