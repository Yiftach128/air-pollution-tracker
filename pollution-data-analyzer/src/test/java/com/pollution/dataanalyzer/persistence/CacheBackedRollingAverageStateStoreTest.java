package com.pollution.dataanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.testing.MutableClock;
import com.pollution.dataanalyzer.entities.Reading;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import com.pollution.persistence.testing.InMemoryPollutionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheBackedRollingAverageStateStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final String PREFIX = "analyzer:rolling-average-state:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration HOUR = Duration.ofHours(1);

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<RollingAverageState> cache =
            new InMemoryPollutionCache<>(RollingAverageState.class, clock);
    private final IRollingAverageStateStore store = new CacheBackedRollingAverageStateStore(cache, PREFIX, TTL);

    private static RollingAverageState state(String sensorId, Pollutant pollutant, double... values) {
        List<Reading> readings = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            readings.add(new Reading(T0.plus(Duration.ofMinutes(i)), values[i]));
            sum += values[i];
        }
        Instant latest = readings.isEmpty() ? null : readings.get(readings.size() - 1).timestamp();
        return new RollingAverageState(new SensorPollutant(sensorId, pollutant), HOUR, sum, latest, readings);
    }

    @Test
    void savesEachSeriesUnderThePrefixSensorAndPollutant() {
        store.save(state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10));

        assertEquals(List.of(PREFIX + "purpleair:Ganei-Ayalon:PM2_5"), cache.keys());
    }

    @Test
    void loadsEverySavedSeries() {
        RollingAverageState one = state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10, 20);
        RollingAverageState two = state("purpleair:Shoham", Pollutant.PM2_5, 30);
        store.save(one);
        store.save(two);

        assertEquals(Set.of(one, two), Set.copyOf(store.loadAll()));
    }

    @Test
    void loadsNothingFromOutsideItsPrefix() {
        cache.setObjectValue("other-service:x", state("purpleair:x", Pollutant.PM2_5, 1));
        store.save(state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10));

        assertEquals(1, store.loadAll().size());
    }

    @Test
    void aSaveReplacesTheSeriesPreviousState() {
        store.save(state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10));
        RollingAverageState newer = state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10, 20);

        store.save(newer);

        assertEquals(List.of(newer), List.copyOf(store.loadAll()));
    }

    @Test
    void aStateNotSavedAgainExpiresAfterTheTtl() {
        store.save(state("purpleair:Ganei-Ayalon", Pollutant.PM2_5, 10));

        clock.advance(TTL.minusSeconds(1));
        assertEquals(1, store.loadAll().size());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(0, store.loadAll().size());
    }

    @Test
    void aStateSurvivesTheTripThroughJson() {
        RollingAverageState state = state("purpleair:Ganei-Ayalon", Pollutant.PM10, 10, 20, 30);

        store.save(state);

        assertEquals(state, store.loadAll().iterator().next());
    }

    @Test
    void rejectsABlankPrefixAndANonPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new CacheBackedRollingAverageStateStore(cache, "", TTL));
        assertThrows(IllegalArgumentException.class, () -> new CacheBackedRollingAverageStateStore(cache, PREFIX, Duration.ZERO));
    }

    @Test
    void closingTheStoreClosesTheCache() {
        store.close();

        assertTrue(cache.isClosed());
    }
}
