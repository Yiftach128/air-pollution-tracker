package com.pollution.common.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * The average concentration of a pollutant at a source over a time window.
 * <p>
 * Its {@link #timestamp()} is the end of the window, i.e. the instant the
 * average became known.
 *
 * @param city         the city the source is located in
 * @param source       identifier of the sensor/station the readings came from
 * @param pollutant    the pollutant averaged
 * @param averageValue mean concentration over the window, in {@link Pollutant#unit()}
 * @param sampleCount  number of readings the average was computed from
 * @param windowStart  start of the averaging window (inclusive)
 * @param windowEnd    end of the averaging window (exclusive)
 */
public record PollutionAverage(String city,
                               String source,
                               Pollutant pollutant,
                               double averageValue,
                               int sampleCount,
                               Instant windowStart,
                               Instant windowEnd)
        implements IMessage {

    public PollutionAverage {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive, was " + sampleCount);
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException(
                    "windowEnd " + windowEnd + " is before windowStart " + windowStart);
        }
    }

    @Override
    public Instant timestamp() {
        return windowEnd;
    }

    @Override
    public String toString() {
        return "PollutionAverage{city=" + city + ", source=" + source
                + ", " + pollutant.displayName() + "=" + averageValue + " " + pollutant.unit()
                + ", samples=" + sampleCount
                + ", window=" + windowStart + ".." + windowEnd + "}";
    }
}
