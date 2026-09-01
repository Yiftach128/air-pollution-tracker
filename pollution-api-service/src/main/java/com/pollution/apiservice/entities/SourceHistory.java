package com.pollution.apiservice.entities;

import com.pollution.persistence.entities.ReadingsSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One source's history over a time range, as the history view shows it:
 * what the range amounts to per pollutant, and the points to draw.
 *
 * @param source  identifier of the sensor/station
 * @param from    start of the range, inclusive
 * @param to      end of the range, exclusive
 * @param bucket  the length the points are averaged over; {@code null} when
 *                every point is a single reading
 * @param summary the range summarized, one per pollutant with readings in it
 * @param points  the readings or bucket averages, oldest first
 */
public record SourceHistory(String source, Instant from, Instant to, Duration bucket,
                            List<ReadingsSummary> summary, List<HistoryPoint> points) {

    public SourceHistory {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        summary = List.copyOf(Objects.requireNonNull(summary, "summary"));
        points = List.copyOf(Objects.requireNonNull(points, "points"));
    }
}
