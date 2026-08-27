package com.pollution.common.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The rolling averages of a pollutant at a source, one per window length
 * (e.g. the last 10 minutes, hour and day), all ending at the same instant.
 * <p>
 * Its {@link #timestamp()} is the end of every window: the newest reading the
 * averages include, i.e. the instant they became known.
 */
public final class PollutionAverage extends AbstractMessage {

    private final String source;
    private final Pollutant pollutant;
    private final List<WindowAverage> averages;

    /**
     * @param city      the city the source is located in
     * @param source    identifier of the sensor/station the readings came from
     * @param pollutant the pollutant averaged
     * @param averages  one average per window, no two over the same window; not empty
     * @param timestamp end of every window: the newest reading the averages include
     */
    public PollutionAverage(String city,
                            String source,
                            Pollutant pollutant,
                            List<WindowAverage> averages,
                            Instant timestamp) {
        super(city, timestamp);
        this.source = Objects.requireNonNull(source, "source");
        this.pollutant = Objects.requireNonNull(pollutant, "pollutant");
        this.averages = List.copyOf(Objects.requireNonNull(averages, "averages"));
        if (this.averages.isEmpty()) {
            throw new IllegalArgumentException("averages must not be empty");
        }
        Set<Duration> windows = new HashSet<>();
        for (WindowAverage average : this.averages) {
            if (!windows.add(average.window())) {
                throw new IllegalArgumentException("duplicate window " + average.window());
            }
        }
    }

    /** Identifier of the sensor/station the readings came from. */
    public String source() {
        return source;
    }

    /** The pollutant averaged. */
    public Pollutant pollutant() {
        return pollutant;
    }

    /** One average per window, in the order they were given; never empty. */
    public List<WindowAverage> averages() {
        return averages;
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
                && averages.equals(that.averages)
                && timestamp().equals(that.timestamp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(city(), source, pollutant, averages, timestamp());
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("PollutionAverage{city=").append(city())
                .append(", source=").append(source)
                .append(", ").append(pollutant.displayName()).append(" in ").append(pollutant.unit());
        for (WindowAverage average : averages) {
            text.append(", ").append(average.window()).append('=').append(average.averageValue())
                    .append(" (").append(average.sampleCount()).append(" samples)");
        }
        return text.append(", at=").append(timestamp()).append('}').toString();
    }
}
