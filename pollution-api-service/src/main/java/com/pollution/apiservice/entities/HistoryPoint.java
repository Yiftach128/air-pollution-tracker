package com.pollution.apiservice.entities;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.entities.BucketAverage;
import java.time.Instant;
import java.util.Objects;

/**
 * One point of a source's history: what one pollutant read at one time,
 * or over one bucket of time. A single reading and a bucket average have
 * the same shape — a reading is a bucket of one — so the page draws both
 * the same way.
 *
 * @param pollutant the pollutant measured
 * @param time      when the reading was taken, or when the bucket starts
 * @param mean      the reading, or the mean of the bucket's readings, in {@link Pollutant#unit()}
 * @param min       the lowest reading in the bucket (the reading itself for a single one)
 * @param max       the highest reading in the bucket (the reading itself for a single one)
 * @param count     readings in the bucket; positive
 */
public record HistoryPoint(Pollutant pollutant, Instant time, double mean, double min, double max, long count) {

    public HistoryPoint {
        Objects.requireNonNull(pollutant, "pollutant");
        Objects.requireNonNull(time, "time");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
    }

    public static HistoryPoint of(PollutionData reading) {
        return new HistoryPoint(reading.pollutant(), reading.timestamp(),
                reading.value(), reading.value(), reading.value(), 1);
    }

    public static HistoryPoint of(BucketAverage average) {
        return new HistoryPoint(average.pollutant(), average.bucketStart(),
                average.mean(), average.min(), average.max(), average.count());
    }
}
