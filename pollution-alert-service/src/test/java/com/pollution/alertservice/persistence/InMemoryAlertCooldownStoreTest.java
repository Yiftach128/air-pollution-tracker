package com.pollution.alertservice.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.testing.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The stand-in for running without a cache follows the same rule as the cache-backed store. */
class InMemoryAlertCooldownStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);

    private final MutableClock clock = MutableClock.at(T0);
    private final IAlertCooldownStore store = new InMemoryAlertCooldownStore(
            new AlertCooldowns(Map.of(), Duration.ofMinutes(15)), clock);

    private static PollutionAlert alert(String source, Pollutant pollutant, Duration window) {
        return new PollutionAlert("Tel Aviv", source, pollutant, window, 60, 50, T0);
    }

    @Test
    void aSeriesCoolsDownForItsCooldownAfterBeingMarkedSent() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, TEN_MINUTES));

        assertEquals(Set.of(new AlertSeries(SOURCE, Pollutant.PM2_5, TEN_MINUTES)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
        clock.advance(TEN_MINUTES);
        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void onlyTheSourceAndPollutantAskedForAreReturned() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));
        store.markSent(alert(SOURCE, Pollutant.PM10, HOUR));
        store.markSent(alert("purpleair:Shoham", Pollutant.PM2_5, HOUR));

        assertEquals(Set.of(new AlertSeries(SOURCE, Pollutant.PM2_5, HOUR)), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }

    @Test
    void closingForgetsEverything() {
        store.markSent(alert(SOURCE, Pollutant.PM2_5, HOUR));

        store.close();

        assertEquals(Set.of(), store.coolingDownSeries(SOURCE, Pollutant.PM2_5));
    }
}
