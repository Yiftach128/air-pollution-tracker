package com.pollution.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.testing.MutableClock;
import com.pollution.persistence.testing.InMemoryPollutionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheBackedLatestReadingStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(60);
    private static final String SOURCE = "purpleair:Ganei-Ayalon";

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<PollutionData> cache = new InMemoryPollutionCache<>(PollutionData.class, clock);
    private final ILatestReadingStore store = new CacheBackedLatestReadingStore(cache, TTL);

    private static PollutionData reading(String source, Pollutant pollutant, double value, Instant at) {
        return new PollutionData("Tel Aviv", source, pollutant, value, at);
    }

    @Test
    void theFirstReadingOfASeriesBecomesItsCurrentOne() {
        PollutionData reading = reading(SOURCE, Pollutant.PM2_5, 12.5, T0);

        assertTrue(store.save(reading));

        assertEquals(Optional.of(reading), store.find(SOURCE, Pollutant.PM2_5));
        assertEquals(List.of("latest-reading:" + SOURCE + ":PM2_5"), cache.keys());
    }

    @Test
    void aNewerReadingReplacesTheCurrentOne() {
        store.save(reading(SOURCE, Pollutant.PM2_5, 12.5, T0));
        PollutionData newer = reading(SOURCE, Pollutant.PM2_5, 14, T0.plusSeconds(10));

        assertTrue(store.save(newer));

        assertEquals(Optional.of(newer), store.find(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void anOlderReadingNeverMovesASeriesBackwards() {
        PollutionData current = reading(SOURCE, Pollutant.PM2_5, 12.5, T0);
        store.save(current);

        assertFalse(store.save(reading(SOURCE, Pollutant.PM2_5, 9, T0.minusSeconds(10))));

        assertEquals(Optional.of(current), store.find(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aRedeliveryOfTheCurrentReadingIsAcceptedAgain() {
        PollutionData current = reading(SOURCE, Pollutant.PM2_5, 12.5, T0);
        store.save(current);

        assertTrue(store.save(current));

        assertEquals(Optional.of(current), store.find(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aSeriesThatStopsReportingDropsOutAfterTheTtl() {
        store.save(reading(SOURCE, Pollutant.PM2_5, 12.5, T0));

        clock.advance(TTL.minusSeconds(1));
        assertTrue(store.find(SOURCE, Pollutant.PM2_5).isPresent());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(store.find(SOURCE, Pollutant.PM2_5).isEmpty());
        assertEquals(List.of(), store.findAll());
    }

    @Test
    void everySaveGivesTheSeriesAFreshTtl() {
        store.save(reading(SOURCE, Pollutant.PM2_5, 12.5, T0));
        clock.advance(Duration.ofMinutes(40));
        store.save(reading(SOURCE, Pollutant.PM2_5, 13, T0.plus(Duration.ofMinutes(40))));
        clock.advance(Duration.ofMinutes(40));

        assertTrue(store.find(SOURCE, Pollutant.PM2_5).isPresent());
    }

    @Test
    void findAllListsTheCurrentReadingOfEverySeries() {
        PollutionData pm25 = reading(SOURCE, Pollutant.PM2_5, 12.5, T0);
        PollutionData pm10 = reading(SOURCE, Pollutant.PM10, 20, T0);
        PollutionData other = reading("purpleair:Shoham", Pollutant.PM2_5, 8, T0);
        store.save(pm25);
        store.save(pm10);
        store.save(other);

        assertEquals(Set.of(pm25, pm10, other), Set.copyOf(store.findAll()));
    }

    @Test
    void seriesAreKeptApartByPollutant() {
        store.save(reading(SOURCE, Pollutant.PM2_5, 12.5, T0));

        assertTrue(store.find(SOURCE, Pollutant.PM10).isEmpty());
    }

    @Test
    void aCacheFailureReachesTheCaller() {
        cache.failWith(new PollutionCacheException("redis is down", null));

        assertThrows(PollutionCacheException.class, () -> store.save(reading(SOURCE, Pollutant.PM2_5, 1, T0)));
        assertThrows(PollutionCacheException.class, store::findAll);
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new CacheBackedLatestReadingStore(cache, Duration.ZERO));
    }

    @Test
    void closingTheStoreClosesTheCache() {
        store.close();

        assertTrue(cache.isClosed());
    }
}
