package com.pollution.common.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * The average concentration of a pollutant at a source over a time window.
 * <p>
 * Its {@link #timestamp()} is the end of the window, i.e. the instant the
 * average became known.
 */
public final class PollutionAverage extends AbstractMessage {

    private final String source;
    private final Pollutant pollutant;
    private final double averageValue;
    private final int sampleCount;
    private final Instant windowStart;
    private final Instant windowEnd;

    /**
     * @param city         the city the source is located in
     * @param source       identifier of the sensor/station the readings came from
     * @param pollutant    the pollutant averaged
     * @param averageValue mean concentration over the window, in {@link Pollutant#unit()}
     * @param sampleCount  number of readings the average was computed from
     * @param windowStart  start of the averaging window (inclusive)
     * @param windowEnd    end of the averaging window (exclusive); becomes the message's {@link #timestamp()}
     */
    public PollutionAverage(String city,
                            String source,
                            Pollutant pollutant,
                            double averageValue,
                            int sampleCount,
                            Instant windowStart,
                            Instant windowEnd) {
        super(city, windowEnd);
        this.source = Objects.requireNonNull(source, "source");
        this.pollutant = Objects.requireNonNull(pollutant, "pollutant");
        this.windowStart = Objects.requireNonNull(windowStart, "windowStart");
        this.windowEnd = windowEnd;
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive, was " + sampleCount);
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException(
                    "windowEnd " + windowEnd + " is before windowStart " + windowStart);
        }
        this.averageValue = averageValue;
        this.sampleCount = sampleCount;
    }

    /** Identifier of the sensor/station the readings came from. */
    public String source() {
        return source;
    }

    /** The pollutant averaged. */
    public Pollutant pollutant() {
        return pollutant;
    }

    /** Mean concentration over the window, in {@link Pollutant#unit()}. */
    public double averageValue() {
        return averageValue;
    }

    /** Number of readings the average was computed from. */
    public int sampleCount() {
        return sampleCount;
    }

    /** Start of the averaging window (inclusive). */
    public Instant windowStart() {
        return windowStart;
    }

    /** End of the averaging window (exclusive); the same instant as {@link #timestamp()}. */
    public Instant windowEnd() {
        return windowEnd;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PollutionAverage that)) {
            return false;
        }
        return city().equals(that.city())
                && source.equals(that.source)
                && pollutant == that.pollutant
                && Double.compare(averageValue, that.averageValue) == 0
                && sampleCount == that.sampleCount
                && windowStart.equals(that.windowStart)
                && timestamp().equals(that.timestamp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(city(), source, pollutant, averageValue, sampleCount, windowStart, timestamp());
    }

    @Override
    public String toString() {
        return "PollutionAverage{city=" + city() + ", source=" + source
                + ", " + pollutant.displayName() + "=" + averageValue + " " + pollutant.unit()
                + ", samples=" + sampleCount
                + ", window=" + windowStart + ".." + windowEnd() + "}";
    }
}
