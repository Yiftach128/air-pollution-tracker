package com.pollution.persistence.entities;

import com.pollution.common.entities.Pollutant;
import java.time.Instant;
import java.util.Objects;

/**
 * One series' readings over one fixed-length slice of time, summarized:
 * what {@link com.pollution.persistence.IPollutionRepository#findAverages}
 * returns for every bucket that has readings.
 *
 * @param pollutant   the pollutant measured
 * @param bucketStart when the bucket begins; the bucket ends one bucket length later
 * @param mean        mean concentration in the bucket, in {@link Pollutant#unit()}
 * @param min         lowest reading in the bucket
 * @param max         highest reading in the bucket
 * @param count       number of readings in the bucket; positive
 */
public record BucketAverage(Pollutant pollutant, Instant bucketStart, double mean, double min, double max, long count) {

    public BucketAverage {
        Objects.requireNonNull(pollutant, "pollutant");
        Objects.requireNonNull(bucketStart, "bucketStart");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
    }
}
