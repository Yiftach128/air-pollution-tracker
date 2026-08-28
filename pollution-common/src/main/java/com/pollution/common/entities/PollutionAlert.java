package com.pollution.common.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Raised when a pollutant's concentration at a source exceeds its threshold:
 * either a single reading did, or the rolling average over one
 * {@link #window() window} did.
 */
public final class PollutionAlert extends AbstractMessage {

    private final String source;
    private final Pollutant pollutant;
    private final Duration window;
    private final double measuredValue;
    private final double threshold;

    /**
     * @param city          the city the source is located in
     * @param source        identifier of the sensor/station where the threshold was exceeded
     * @param pollutant     the pollutant that exceeded its threshold
     * @param window        the rolling-average window whose mean exceeded the threshold,
     *                      or {@code null} when a single reading did; positive if given
     * @param measuredValue the concentration that triggered the alert — the reading, or
     *                      the window's mean — in {@link Pollutant#unit()}
     * @param threshold     the threshold that was exceeded, in {@link Pollutant#unit()}
     * @param timestamp     the instant of the measurement that triggered the alert
     */
    public PollutionAlert(String city,
                          String source,
                          Pollutant pollutant,
                          Duration window,
                          double measuredValue,
                          double threshold,
                          Instant timestamp) {
        super(city, timestamp);
        this.source = Objects.requireNonNull(source, "source");
        this.pollutant = Objects.requireNonNull(pollutant, "pollutant");
        if (window != null && (window.isZero() || window.isNegative())) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
        this.window = window;
        this.measuredValue = measuredValue;
        this.threshold = threshold;
    }

    /** Identifier of the sensor/station where the threshold was exceeded. */
    public String source() {
        return source;
    }

    /** The pollutant that exceeded its threshold. */
    public Pollutant pollutant() {
        return pollutant;
    }

    /**
     * The rolling-average window whose mean exceeded the threshold, or
     * {@code null} when a single reading did.
     */
    public Duration window() {
        return window;
    }

    /** The concentration that triggered the alert, in {@link Pollutant#unit()}. */
    public double measuredValue() {
        return measuredValue;
    }

    /** The threshold that was exceeded, in {@link Pollutant#unit()}. */
    public double threshold() {
        return threshold;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PollutionAlert that)) {
            return false;
        }
        return city().equals(that.city())
                && source.equals(that.source)
                && pollutant == that.pollutant
                && Objects.equals(window, that.window)
                && Double.compare(measuredValue, that.measuredValue) == 0
                && Double.compare(threshold, that.threshold) == 0
                && timestamp().equals(that.timestamp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(city(), source, pollutant, window, measuredValue, threshold, timestamp());
    }

    @Override
    public String toString() {
        return "PollutionAlert{city=" + city() + ", source=" + source
                + ", " + pollutant.displayName() + "=" + measuredValue
                + " exceeds " + threshold + " " + pollutant.unit()
                + (window == null ? " (reading)" : " over " + window)
                + ", at=" + timestamp() + "}";
    }
}
