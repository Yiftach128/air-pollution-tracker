package com.pollution.apiservice.queries;

import com.pollution.apiservice.entities.HistoryPoint;
import com.pollution.apiservice.entities.SourceHistory;
import com.pollution.persistence.IPollutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Answers "what did this source read over that time" from the repository:
 * the range summarized per pollutant, and the points to draw — every
 * reading, or the readings averaged over buckets of a chosen length, which
 * is what keeps a month of readings drawable. Fills in the range when the
 * caller leaves it open (up to now, reaching back a default length) and
 * refuses one longer than the configured maximum, so a request cannot pull
 * the whole table.
 */
public final class HistoryQuery {

    private final IPollutionRepository repository;
    private final Duration defaultRange;
    private final Duration maxRange;

    /**
     * @param defaultRange how far back a history reaches when {@code from} is not given; positive
     * @param maxRange     the longest range allowed; not shorter than {@code defaultRange}
     */
    public HistoryQuery(IPollutionRepository repository, Duration defaultRange, Duration maxRange) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultRange = Objects.requireNonNull(defaultRange, "defaultRange");
        this.maxRange = Objects.requireNonNull(maxRange, "maxRange");
        if (defaultRange.isNegative() || defaultRange.isZero()) {
            throw new IllegalArgumentException("defaultRange must be positive, was " + defaultRange);
        }
        if (maxRange.compareTo(defaultRange) < 0) {
            throw new IllegalArgumentException("maxRange " + maxRange + " is shorter than defaultRange " + defaultRange);
        }
    }

    /**
     * @param from   start of the range, inclusive; {@code null} for {@code to} minus the default range
     * @param to     end of the range, exclusive; {@code null} for now
     * @param bucket the length to average over; {@code null} for every reading as it is
     * @throws IllegalArgumentException if the range is empty, longer than the maximum, or the bucket not positive
     */
    public SourceHistory history(String source, Instant from, Instant to, Duration bucket) {
        Objects.requireNonNull(source, "source");
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(defaultRange);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from " + start + " is not before to " + end);
        }
        if (Duration.between(start, end).compareTo(maxRange) > 0) {
            throw new IllegalArgumentException("range " + Duration.between(start, end)
                    + " is longer than the most allowed, " + maxRange);
        }
        if (bucket != null && (bucket.isNegative() || bucket.isZero())) {
            throw new IllegalArgumentException("bucket must be positive, was " + bucket);
        }
        List<HistoryPoint> points = bucket == null
                ? repository.findReadings(source, start, end).stream().map(HistoryPoint::of).toList()
                : repository.findAverages(source, start, end, bucket).stream().map(HistoryPoint::of).toList();
        return new SourceHistory(source, start, end, bucket, repository.summarize(source, start, end), points);
    }
}
