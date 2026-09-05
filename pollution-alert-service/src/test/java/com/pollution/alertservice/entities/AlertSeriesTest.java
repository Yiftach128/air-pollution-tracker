package com.pollution.alertservice.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertSeriesTest {

    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);

    private static AlertSeries series(String source, Pollutant pollutant, Duration window) {
        return new AlertSeries(source, pollutant, window);
    }

    @Test
    void anAlertBelongsToTheSeriesOfItsSourcePollutantAndWindow() {
        PollutionAlert alert = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, HOUR, 40, 31.25,
                Instant.parse("2026-09-05T10:00:00Z"));

        assertEquals(series(SOURCE, Pollutant.PM2_5, HOUR), AlertSeries.of(alert));
    }

    @Test
    void anyWindowSupersedesASingleReading() {
        assertTrue(series(SOURCE, Pollutant.PM2_5, TEN_MINUTES).supersedes(series(SOURCE, Pollutant.PM2_5, null)));
        assertTrue(series(SOURCE, Pollutant.PM2_5, HOUR).supersedes(series(SOURCE, Pollutant.PM2_5, null)));
    }

    @Test
    void aLongerWindowSupersedesAShorterOneAndNotTheOtherWayRound() {
        assertTrue(series(SOURCE, Pollutant.PM2_5, HOUR).supersedes(series(SOURCE, Pollutant.PM2_5, TEN_MINUTES)));
        assertFalse(series(SOURCE, Pollutant.PM2_5, TEN_MINUTES).supersedes(series(SOURCE, Pollutant.PM2_5, HOUR)));
    }

    @Test
    void aSingleReadingSupersedesNothing() {
        assertFalse(series(SOURCE, Pollutant.PM2_5, null).supersedes(series(SOURCE, Pollutant.PM2_5, TEN_MINUTES)));
        assertFalse(series(SOURCE, Pollutant.PM2_5, null).supersedes(series(SOURCE, Pollutant.PM2_5, null)));
    }

    @Test
    void aSeriesNeverSupersedesItself() {
        assertFalse(series(SOURCE, Pollutant.PM2_5, HOUR).supersedes(series(SOURCE, Pollutant.PM2_5, HOUR)));
    }

    @Test
    void onlyTheSameSourceAndPollutantCount() {
        assertFalse(series(SOURCE, Pollutant.PM2_5, HOUR).supersedes(series("purpleair:Shoham", Pollutant.PM2_5, null)));
        assertFalse(series(SOURCE, Pollutant.PM2_5, HOUR).supersedes(series(SOURCE, Pollutant.PM10, null)));
    }

    @Test
    void namesItselfBySourcePollutantAndWindow() {
        assertEquals(SOURCE + "/PM2.5@PT1H", series(SOURCE, Pollutant.PM2_5, HOUR).toString());
        assertEquals(SOURCE + "/PM2.5", series(SOURCE, Pollutant.PM2_5, null).toString());
    }
}
