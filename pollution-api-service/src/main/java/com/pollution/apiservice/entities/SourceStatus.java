package com.pollution.apiservice.entities;

import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.SourceId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One source as the overview shows it: who it is, what it is reading right
 * now, if anything, and when it last reported at all. The identifier is
 * handed out both whole — the key the stores and {@code /api/history} know
 * the source by — and taken apart into provider and sensor, so the page
 * never has to know how identifiers are spelled.
 *
 * @param source          identifier of the sensor/station, as the stores know it
 *                        ({@code purpleair:Ganei-Ayalon})
 * @param provider        who the readings come from ({@code purpleair}); {@code null}
 *                        when the identifier is not a {@link SourceId}
 * @param sensor          the provider's name for the sensor; the whole identifier
 *                        when it has no provider
 * @param city            the city it is in
 * @param lastReportedAt  the timestamp of its newest reading, current or not
 * @param currentReadings its current reading per pollutant, ordered by pollutant;
 *                        empty when it has none (it stopped reporting)
 */
public record SourceStatus(String source, String provider, String sensor, String city, Instant lastReportedAt,
                           List<PollutionData> currentReadings) {

    public SourceStatus {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(lastReportedAt, "lastReportedAt");
        currentReadings = List.copyOf(Objects.requireNonNull(currentReadings, "currentReadings"));
    }

    /** The status of a source, its identifier taken apart with {@link SourceId#tryParse}. */
    public static SourceStatus of(String source, String city, Instant lastReportedAt,
                                  List<PollutionData> currentReadings) {
        Optional<SourceId> id = SourceId.tryParse(source);
        return new SourceStatus(source,
                id.map(SourceId::provider).orElse(null),
                id.map(SourceId::sensor).orElse(source),
                city, lastReportedAt, currentReadings);
    }
}
