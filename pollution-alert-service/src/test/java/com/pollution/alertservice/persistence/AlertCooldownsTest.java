package com.pollution.alertservice.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertCooldownsTest {

    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);

    private final AlertCooldowns cooldowns = new AlertCooldowns(Map.of(HOUR, Duration.ofMinutes(45)), Duration.ofMinutes(15));

    private static AlertSeries series(Duration window) {
        return new AlertSeries(SOURCE, Pollutant.PM2_5, window);
    }

    @Test
    void aWindowWithAnExplicitCooldownUsesIt() {
        assertEquals(Duration.ofMinutes(45), cooldowns.cooldownOf(series(HOUR)));
    }

    @Test
    void aWindowWithoutOneStaysQuietForItsOwnLength() {
        assertEquals(TEN_MINUTES, cooldowns.cooldownOf(series(TEN_MINUTES)));
    }

    @Test
    void aSingleReadingUsesTheReadingCooldown() {
        assertEquals(Duration.ofMinutes(15), cooldowns.cooldownOf(series(null)));
    }

    @Test
    void cooldownsMustBePositive() {
        Map<Duration, Duration> missing = new HashMap<>();
        missing.put(HOUR, null);

        assertThrows(IllegalArgumentException.class, () -> new AlertCooldowns(Map.of(HOUR, Duration.ZERO), TEN_MINUTES));
        assertThrows(IllegalArgumentException.class, () -> new AlertCooldowns(missing, TEN_MINUTES));
        assertThrows(IllegalArgumentException.class, () -> new AlertCooldowns(Map.of(), Duration.ofMinutes(-1)));
        assertThrows(IllegalArgumentException.class, () -> new AlertCooldowns(Map.of(), null));
    }
}
