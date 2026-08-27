package com.pollution.common.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * A single raw pollution reading reported by a source (sensor or station).
 */
public final class PollutionData extends AbstractMessage {

    private final String source;
    private final Pollutant pollutant;
    private final double value;

    /**
     * @param city      the city the source is located in
     * @param source    identifier of the sensor/station that produced the reading
     * @param pollutant the pollutant measured
     * @param value     measured concentration, in {@link Pollutant#unit()}
     * @param timestamp when the reading was taken
     */
    public PollutionData(String city, String source, Pollutant pollutant, double value, Instant timestamp) {
        super(city, timestamp);
        this.source = Objects.requireNonNull(source, "source");
        this.pollutant = Objects.requireNonNull(pollutant, "pollutant");
        this.value = value;
    }

    /** Identifier of the sensor/station that produced the reading. */
    public String source() {
        return source;
    }

    /** The pollutant measured. */
    public Pollutant pollutant() {
        return pollutant;
    }

    /** Measured concentration, in {@link Pollutant#unit()}. */
    public double value() {
        return value;
    }

    /** A copy of this reading carrying a different timestamp. */
    public PollutionData withTimestamp(Instant newTimestamp) {
        return new PollutionData(city(), source, pollutant, value, newTimestamp);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PollutionData that)) {
            return false;
        }
        return city().equals(that.city())
                && source.equals(that.source)
                && pollutant == that.pollutant
                && Double.compare(value, that.value) == 0
                && timestamp().equals(that.timestamp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(city(), source, pollutant, value, timestamp());
    }

    @Override
    public String toString() {
        return "PollutionData{city=" + city() + ", source=" + source
                + ", " + pollutant.displayName() + "=" + value + " " + pollutant.unit()
                + ", at=" + timestamp() + "}";
    }
}
