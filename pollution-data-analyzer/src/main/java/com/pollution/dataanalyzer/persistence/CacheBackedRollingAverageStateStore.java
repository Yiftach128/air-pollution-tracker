package com.pollution.dataanalyzer.persistence;

import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import com.pollution.persistence.IPollutionCache;
import java.util.Collection;
import java.util.Objects;

/**
 * Keeps each series' state in an {@link IPollutionCache} under the key
 * {@code <keyPrefix><sensorId>:<POLLUTANT>}. The prefix is the analyzer's
 * namespace in the shared cache, so loading everything under it never picks
 * up another service's keys. Works with any cache implementation; the
 * concrete one is chosen in Wiring.
 */
public final class CacheBackedRollingAverageStateStore implements IRollingAverageStateStore {

    private final IPollutionCache<RollingAverageState> cache;
    private final String keyPrefix;

    public CacheBackedRollingAverageStateStore(IPollutionCache<RollingAverageState> cache, String keyPrefix) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
    }

    @Override
    public void save(RollingAverageState state) {
        cache.setObjectValue(keyOf(state.sensorPollutant()), state);
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
