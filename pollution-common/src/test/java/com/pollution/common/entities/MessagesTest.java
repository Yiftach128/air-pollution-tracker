package com.pollution.common.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The validation and value semantics of the messages the services exchange. */
class MessagesTest {

    private static final Instant AT = Instant.parse("2026-09-05T10:00:00Z");
    private static final String SOURCE = "purpleair:Ganei-Ayalon";

    @Test
    void aReadingWithAnotherTimestampKeepsEverythingElse() {
        PollutionData reading = new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 12.5, AT);

        PollutionData later = reading.withTimestamp(AT.plusSeconds(10));

        assertEquals(AT.plusSeconds(10), later.timestamp());
        assertEquals(reading.city(), later.city());
        assertEquals(reading.source(), later.source());
        assertEquals(reading.pollutant(), later.pollutant());
        assertEquals(reading.value(), later.value());
        assertNotEquals(reading, later);
    }

    @Test
    void messagesAreEqualByValue() {
        PollutionData one = new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 12.5, AT);
        PollutionData same = new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 12.5, AT);

        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
    }

    @Test
    void anAverageNeedsAtLeastOneWindowAndNoWindowTwice() {
        WindowAverage tenMinutes = new WindowAverage(Duration.ofMinutes(10), 10, 3);
        WindowAverage tenMinutesAgain = new WindowAverage(Duration.ofMinutes(10), 11, 4);

        assertThrows(IllegalArgumentException.class,
                () -> new PollutionAverage("Tel Aviv", SOURCE, Pollutant.PM2_5, List.of(), AT));
        assertThrows(IllegalArgumentException.class,
                () -> new PollutionAverage("Tel Aviv", SOURCE, Pollutant.PM2_5, List.of(tenMinutes, tenMinutesAgain), AT));
    }

    @Test
    void anAverageKeepsItsWindowsInTheGivenOrder() {
        WindowAverage hour = new WindowAverage(Duration.ofHours(1), 10, 30);
        WindowAverage tenMinutes = new WindowAverage(Duration.ofMinutes(10), 12, 5);

        PollutionAverage average = new PollutionAverage("Tel Aviv", SOURCE, Pollutant.PM2_5, List.of(hour, tenMinutes), AT);

        assertEquals(List.of(hour, tenMinutes), average.averages());
    }

    @Test
    void aWindowAverageNeedsAPositiveWindowAndSampleCount() {
        assertThrows(IllegalArgumentException.class, () -> new WindowAverage(Duration.ZERO, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WindowAverage(Duration.ofMinutes(-1), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WindowAverage(Duration.ofMinutes(1), 1, 0));
    }

    @Test
    void anAlertHasNoWindowForASingleReadingAndAPositiveOneOtherwise() {
        PollutionAlert reading = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, null, 60, 50, AT);
        PollutionAlert hour = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, Duration.ofHours(1), 40, 31.25, AT);

        assertNull(reading.window());
        assertEquals(Duration.ofHours(1), hour.window());
        assertThrows(IllegalArgumentException.class,
                () -> new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, Duration.ZERO, 40, 25, AT));
    }

    @Test
    void aMessageNeedsACityAndATimestamp() {
        assertThrows(NullPointerException.class, () -> new PollutionData(null, SOURCE, Pollutant.PM2_5, 1, AT));
        assertThrows(NullPointerException.class, () -> new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 1, null));
    }
}
