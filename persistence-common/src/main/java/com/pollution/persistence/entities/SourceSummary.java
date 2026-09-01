package com.pollution.persistence.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * One source the repository holds readings of, as listed by
 * {@link com.pollution.persistence.IPollutionRepository#findSources()}.
 *
 * @param source         identifier of the sensor/station
 * @param city           the city of its newest reading
 * @param lastReportedAt the timestamp of its newest reading, of any pollutant
 */
public record SourceSummary(String source, String city, Instant lastReportedAt) {

    public SourceSummary {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(lastReportedAt, "lastReportedAt");
    }
}
