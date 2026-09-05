package com.pollution.dataanalyzer.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.WindowAverage;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RollingAverageGroupTest {

    private static final SensorPollutant SERIES = new SensorPollutant("purpleair:Ganei-Ayalon", Pollutant.PM2_5);
    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    /** Deliberately not ascending: the group sorts them. */
    private static final List<Duration> WINDOWS = List.of(HOUR, TEN_MINUTES);

    private final RollingAverageGroup group = new RollingAverageGroup(SERIES, WINDOWS);

    private static Instant minutes(long minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    @Test
    void windowsAreSortedAscendingAndValidated() {
        assertEquals(List.of(TEN_MINUTES, HOUR), RollingAverageGroup.canonicalWindows(WINDOWS));
        assertThrows(IllegalArgumentException.class, () -> RollingAverageGroup.canonicalWindows(List.of()));
        assertThrows(IllegalArgumentException.class, () -> RollingAverageGroup.canonicalWindows(List.of(Duration.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> RollingAverageGroup.canonicalWindows(List.of(HOUR, HOUR)));
        assertThrows(NullPointerException.class, () -> RollingAverageGroup.canonicalWindows(Arrays.asList(HOUR, null)));
    }

    @Test
    void hasNoAveragesUntilTheFirstReading() {
        assertEquals(List.of(), group.averages());
        assertNull(group.latest());
    }

    @Test
    void givesOneAveragePerWindowShortestFirst() {
        group.addReading(minutes(0), 10);

        assertEquals(List.of(new WindowAverage(TEN_MINUTES, 10, 1), new WindowAverage(HOUR, 10, 1)), group.averages());
    }

    @Test
    void everyWindowKeepsWhatFallsInsideIt() {
        group.addReading(minutes(0), 10);
        group.addReading(minutes(5), 30);
        group.addReading(minutes(30), 50);

        assertEquals(List.of(new WindowAverage(TEN_MINUTES, 50, 1), new WindowAverage(HOUR, 30, 3)), group.averages());
        assertEquals(minutes(30), group.latest());
    }

    @Test
    void theVerdictIsWhetherTheLongestWindowAcceptedTheReading() {
        assertTrue(group.addReading(minutes(0), 10));
        assertFalse(group.addReading(minutes(0), 10));
        assertFalse(group.addReading(minutes(-1), 10));
        assertTrue(group.addReading(minutes(1), 10));
    }

    @Test
    void thePersistentStateIsTheLongestWindows() {
        group.addReading(minutes(0), 10);
        group.addReading(minutes(30), 50);

        RollingAverageState state = group.getPersistentState();

        assertEquals(HOUR, state.window());
        assertEquals(2, state.sampleCount());
        assertEquals(SERIES, state.sensorPollutant());
    }

    @Test
    void everyWindowIsRestoredFromTheLongestOnesState() {
        group.addReading(minutes(0), 10);
        group.addReading(minutes(5), 30);
        group.addReading(minutes(30), 50);

        RollingAverageGroup restored = RollingAverageGroup.loadFromPersistence(group.getPersistentState(), WINDOWS);

        assertEquals(group.averages(), restored.averages());
        assertEquals(group.latest(), restored.latest());
        assertEquals(SERIES, restored.sensorPollutant());

        group.addReading(minutes(31), 70);
        restored.addReading(minutes(31), 70);
        assertEquals(group.averages(), restored.averages());
    }
}
