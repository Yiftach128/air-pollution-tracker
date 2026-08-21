package com.pollution.common.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * A single raw pollution reading reported by a source (sensor or station).
 *
 * @param city      the city the source is located in
 * @param source    identifier of the sensor/station that produced the reading
 * @param pollutant the pollutant measured
 * @param value     measured concentration, in {@link Pollutant#unit()}
 * @param timestamp when the reading was taken
 */
public record PollutionData(String city, String source, Pollutant pollutant, double value, Instant timestamp)
        implements IMessage {

    public PollutionData {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** A copy of this reading carrying a different timestamp. */
    public PollutionData withTimestamp(Instant newTimestamp) {
        return new PollutionData(city, source, pollutant, value, newTimestamp);
    }

    @Override
    public String toString() {
        return "PollutionData{city=" + city + ", source=" + source
                + ", " + pollutant.displayName() + "=" + value + " " + pollutant.unit()
                + ", at=" + timestamp + "}";
    }
}
