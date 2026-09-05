package com.pollution.apiservice.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.thresholds.Thresholds;
import com.pollution.persistence.entities.BucketAverage;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How the API's own value types are built from the stores' and the shared ones. */
class ApiEntitiesTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void aSourceStatusTakesTheIdentifierApart() {
        SourceStatus status = SourceStatus.of("purpleair:Ganei-Ayalon", "Tel Aviv", T0, List.of());

        assertEquals("purpleair:Ganei-Ayalon", status.source());
        assertEquals("purpleair", status.provider());
        assertEquals("Ganei-Ayalon", status.sensor());
    }

    @Test
    void anIdentifierThatIsNotASourceIdHasNoProviderAndIsTheWholeSensor() {
        SourceStatus status = SourceStatus.of("308702", "Tel Aviv", T0, List.of());

        assertNull(status.provider());
        assertEquals("308702", status.sensor());
    }

    @Test
    void aSingleReadingIsAPointOfOne() {
        PollutionData reading = new PollutionData("Tel Aviv", "purpleair:x", Pollutant.PM2_5, 12.5, T0);

        assertEquals(new HistoryPoint(Pollutant.PM2_5, T0, 12.5, 12.5, 12.5, 1), HistoryPoint.of(reading));
    }

    @Test
    void aBucketAverageIsAPointAtTheBucketsStart() {
        BucketAverage average = new BucketAverage(Pollutant.PM10, T0, 11, 10, 12, 6);

        assertEquals(new HistoryPoint(Pollutant.PM10, T0, 11, 10, 12, 6), HistoryPoint.of(average));
    }

    @Test
    void aPointNeedsAPositiveCount() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryPoint(Pollutant.PM2_5, T0, 1, 1, 1, 0));
    }

    @Test
    void aPollutantInfoCarriesTheEffectiveThresholdPerMeasurementLength() {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, pollutant == Pollutant.PM2_5 ? 25.0 : 100.0);
        }
        Thresholds thresholds = new Thresholds(baselines,
                Map.of(Duration.ofHours(24), 1.0, Duration.ofHours(1), 1.25, Duration.ofMinutes(10), 1.35), 2);

        PollutantInfo info = PollutantInfo.of(Pollutant.PM2_5, thresholds);

        assertEquals("PM2_5", info.name());
        assertEquals("PM2.5", info.displayName());
        assertEquals("µg/m³", info.unit());
        assertEquals(List.of("raw", "PT10M", "PT1H", "PT24H"), List.copyOf(info.thresholds().keySet()));
        assertEquals(List.of(50.0, 33.75, 31.25, 25.0), List.copyOf(info.thresholds().values()));
    }

    @Test
    void theLimitsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryLimits(0, 365));
        assertThrows(IllegalArgumentException.class, () -> new HistoryLimits(24, 0));
    }
}
