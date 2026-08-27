package com.pollution.common.entities;

import java.time.Duration;
import java.util.Objects;

/**
 * The mean of a pollutant's readings at a source over one rolling window, as
 * carried inside a {@link PollutionAverage}. Not a message on its own.
 *
 * @param window       how far back from the message's timestamp the window reaches; positive
 * @param averageValue mean concentration over the window, in {@link Pollutant#unit()}
 * @param sampleCount  number of readings the average was computed from; positive
 */
public record WindowAverage(Duration window, double averageValue, int sampleCount) {

    public WindowAverage {
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive, was " + sampleCount);
        }
    }
}
