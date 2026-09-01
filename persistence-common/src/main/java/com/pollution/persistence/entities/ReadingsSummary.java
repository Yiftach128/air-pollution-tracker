package com.pollution.persistence.entities;

import com.pollution.common.entities.Pollutant;
import java.util.Objects;

/**
 * What one series' readings over a time range amount to, computed by the
 * repository rather than from the readings themselves.
 *
 * @param pollutant the pollutant measured
 * @param mean      mean concentration over the range, in {@link Pollutant#unit()}
 * @param min       lowest reading in the range
 * @param max       highest reading in the range
 * @param count     number of readings in the range; positive
 */
public record ReadingsSummary(Pollutant pollutant, double mean, double min, double max, long count) {

    public ReadingsSummary {
        Objects.requireNonNull(pollutant, "pollutant");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
    }
}
