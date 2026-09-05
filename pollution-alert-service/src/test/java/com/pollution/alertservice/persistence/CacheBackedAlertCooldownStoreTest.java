package com.pollution.alertservice.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.testing.MutableClock;
import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.testing.InMemoryPollutionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheBackedAlertCooldownStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final String PREFIX = "alert:last-sent:";
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration HOUR_COOLDOWN = Duration.ofMinutes(45);
    private static final Duration READING_COOLDOWN = Duration.ofMinutes(15);

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<PollutionAlert> cache = new InMemoryPollutionCache<>(PollutionAlert.class, clock);
    private final AlertCooldowns cooldowns = new AlertCooldowns(Map.of(HOUR, HOUR_COOLDOWN), READING_COOLDOWN);
    private final IAlertCooldownStore store = new CacheBackedAlertCooldownStore(cache, PREFIX, cooldowns);

    private static PollutionAlert alert(String source, Pollutant pollutant, Duration window) {
        return new PollutionAlert("Tel Aviv", source, pollutant, window, 60, 50, T0);
    }

    private static AlertSeries series(String source, Pollutant pollutant, Duration window) {
        return new AlertSeries(source, pollutant, window);
    }

    @Test
    void nothingIsCoolingDownUntilAnAlertWasMarkedSent() {
        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aSeriesIsCoolingDownOnceItsAlertWasMarkedSent() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));

        assertEquals(Set.of(series(SOURCE, Pollutant.PM2_5, HOUR)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void theCooldownEndsExactlyWhenItsTimeHasPassed() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));

        clock.advance(HOUR_COOLDOWN.minusSeconds(1));
        assertEquals(1, store.coolingDownSeries(SOURCE, Pollutant.PM2_5).size());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aWindowWithoutAnExplicitCooldownStaysQuietForItsOwnLength() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, TEN_MINUTES));

        clock.advance(TEN_MINUTES.minusSeconds(1));
        assertEquals(1, store.coolingDownSeries(SOURCE, Pollutant.PM2_5).size());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aSingleReadingUsesTheReadingCooldown() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, null));

        clock.advance(READING_COOLDOWN.minusSeconds(1));
        assertEquals(Set.of(series(SOURCE, Pollutant.PM2_5, null)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void keysNameThePrefixSourcePollutantAndWindow() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));
        store.markSent(alert(SOURCE, Pollutant.PM2_5, null));

        assertEquals(List.of(PREFIX + SOURCE + ":PM2_5:PT1H", PREFIX + SOURCE + ":PM2_5:raw"), cache.keys());
    }

    @Test
    void everySeriesOfTheSourceAndPollutantIsFoundTogether() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));
        store.markSent(alert(SOURCE, Pollutant.PM2_5, TEN_MINUTES));
        store.markSent(alert(SOURCE, Pollutant.PM2_5, null));

        assertEquals(Set.of(
                series(SOURCE, Pollutant.PM2_5, HOUR),
                series(SOURCE, Pollutant.PM2_5, TEN_MINUTES),
                series(SOURCE, Pollutant.PM2_5, null)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void otherSourcesAndPollutantsAreLeftOut() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));
        store.markSent(alert("purpleair:Shoham", Pollutant.PM2_5, HOUR));
        store.markSent(alert(SOURCE, Pollutant.PM10, HOUR));

        assertEquals(Set.of(series(SOURCE, Pollutant.PM2_5, HOUR)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void aSourceWhoseNameContinuesAnothersKeyDoesNotLeakIn() {
        // its key, alert:last-sent:purpleair:x:PM2_5:PM2_5:PT1H, matches the pattern of source purpleair:x
        String longer = "purpleair:x:PM2_5";
        store.markSent(alert(longer, Pollutant.PM2_5, HOUR));

        assertEquals(Set.of(), store.coolingDownSeries("purpleair:x", Pollutant.PM2_5));
        assertEquals(Set.of(series(longer, Pollutant.PM2_5, HOUR)), store.coolingDownSeries(longer, Pollutant.PM2_5));
    }

    @Test
    void aGlobCharacterInASourceNameMatchesOnlyItself() {
        store.markSent(alert("purpleair:s*", Pollutant.PM2_5, HOUR));
        store.markSent(alert("purpleair:sX", Pollutant.PM2_5, HOUR));

        assertEquals(Set.of(series("purpleair:s*", Pollutant.PM2_5, HOUR)), store.coolingDownSeries("purpleair:s*", Pollutant.PM2_5));
        assertEquals(Set.of(series("purpleair:sX", Pollutant.PM2_5, HOUR)), store.coolingDownSeries("purpleair:sX", Pollutant.PM2_5));
    }

    @Test
    void markingASeriesAgainRestartsItsCooldown() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, TEN_MINUTES));
        clock.advance(Duration.ofMinutes(8));
        store.markSent(alert(SOURCE, Pollutant.PM2_5, TEN_MINUTES));
        clock.advance(Duration.ofMinutes(8));

        assertEquals(1, store.coolingDownSeries(SOURCE, Pollutant.PM2_5).size());
    }

    @Test
    void aCacheFailureReachesTheCallerToDecide() {
        cache.failWith(new PollutionCacheException("redis is down", null));

        assertThrows(PollutionCacheException.class, () -> store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
        assertThrows(PollutionCacheException.class, () -> store.markSent(alert(SOURCE, Pollutant.PM2_5, null)));
    }

    @Test
    void rejectsABlankPrefix() {
        assertThrows(IllegalArgumentException.class, () -> new CacheBackedAlertCooldownStore(cache, " ", cooldowns));
    }

    @Test
    void closingTheStoreClosesTheCache() {
        store.close();

        assertTrue(cache.isClosed());
    }
}
