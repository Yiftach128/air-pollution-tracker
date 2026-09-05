package com.pollution.dataanalyzer.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.dataanalyzer.entities.Reading;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class RollingAverageTest {

    private static final SensorPollutant SERIES = new SensorPollutant("purpleair:Ganei-Ayalon", Pollutant.PM2_5);
    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final RollingAverage average = new RollingAverage(SERIES, WINDOW);

    private static Instant minutes(long minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    @Test
    void isEmptyUntilTheFirstReading() {
        assertEquals(OptionalDouble.empty(), average.calculateAverage());
        assertEquals(0, average.size());
        assertNull(average.latest());
    }

    @Test
    void averagesEveryReadingInTheWindow() {
        assertTrue(average.addReading(minutes(0), 10));
        assertTrue(average.addReading(minutes(1), 20));
        assertTrue(average.addReading(minutes(2), 30));

        assertEquals(OptionalDouble.of(20), average.calculateAverage());
        assertEquals(3, average.size());
        assertEquals(minutes(2), average.latest());
    }

    @Test
    void aReadingExactlyOneWindowOldIsStillInside() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(10), 30);

        assertEquals(OptionalDouble.of(20), average.calculateAverage());
        assertEquals(2, average.size());
    }

    @Test
    void readingsThatFallOutOfTheWindowAreEvictedAsNewerOnesArrive() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(5), 20);
        average.addReading(minutes(11), 40);

        assertEquals(OptionalDouble.of(30), average.calculateAverage());
        assertEquals(2, average.size());
    }

    @Test
    void aReadingNotNewerThanTheNewestIsARepeatAndIgnored() {
        average.addReading(minutes(5), 20);

        assertFalse(average.addReading(minutes(5), 99));
        assertFalse(average.addReading(minutes(4), 99));

        assertEquals(OptionalDouble.of(20), average.calculateAverage());
        assertEquals(1, average.size());
        assertTrue(average.addReading(minutes(6), 40));
    }

    @Test
    void theNewestReadingIsNeverEvictedHoweverLongTheGap() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(180), 50);

        assertEquals(OptionalDouble.of(50), average.calculateAverage());
        assertEquals(1, average.size());
    }

    @Test
    void theStateIsASnapshotThatLaterReadingsDoNotChange() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(1), 20);

        RollingAverageState state = average.getState();
        average.addReading(minutes(2), 30);

        assertEquals(SERIES, state.sensorPollutant());
        assertEquals(WINDOW, state.window());
        assertEquals(30, state.sum());
        assertEquals(minutes(1), state.latest());
        assertEquals(List.of(new Reading(minutes(0), 10), new Reading(minutes(1), 20)), state.readings());
        assertEquals(OptionalDouble.of(15), state.average());
    }

    @Test
    void restoresFromItsOwnStateAndCarriesOn() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(5), 20);
        average.addReading(minutes(10), 30);

        RollingAverage restored = RollingAverage.loadFromPersistence(average.getState(), WINDOW);

        assertEquals(average.calculateAverage(), restored.calculateAverage());
        assertEquals(average.size(), restored.size());
        assertEquals(average.latest(), restored.latest());
        assertFalse(restored.addReading(minutes(10), 99), "the persisted newest reading is still the newest");
        assertTrue(restored.addReading(minutes(11), 40));
        assertEquals(OptionalDouble.of(30), restored.calculateAverage());
    }

    @Test
    void aShorterWindowIsDerivedByDroppingWhatFallsOutsideIt() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(5), 20);
        average.addReading(minutes(10), 30);

        RollingAverage fiveMinutes = RollingAverage.loadFromPersistence(average.getState(), Duration.ofMinutes(5));

        assertEquals(Duration.ofMinutes(5), fiveMinutes.window());
        assertEquals(2, fiveMinutes.size());
        assertEquals(OptionalDouble.of(25), fiveMinutes.calculateAverage());
        assertEquals(minutes(10), fiveMinutes.latest());
        assertEquals(50, fiveMinutes.getState().sum());
    }

    @Test
    void aLongerWindowKeepsEveryPersistedReading() {
        average.addReading(minutes(0), 10);
        average.addReading(minutes(5), 20);
        average.addReading(minutes(10), 30);

        RollingAverage hour = RollingAverage.loadFromPersistence(average.getState(), Duration.ofHours(1));

        assertEquals(3, hour.size());
        assertEquals(OptionalDouble.of(20), hour.calculateAverage());
    }

    @Test
    void anEmptyStateRestoresEmpty() {
        RollingAverageState empty = new RollingAverageState(SERIES, WINDOW, 0, null, List.of());

        RollingAverage restored = RollingAverage.loadFromPersistence(empty, WINDOW);

        assertEquals(0, restored.size());
        assertNull(restored.latest());
        assertTrue(restored.addReading(minutes(0), 10));
    }

    @Test
    void rejectsANonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new RollingAverage(SERIES, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RollingAverage(SERIES, Duration.ofMinutes(-1)));
    }
}
