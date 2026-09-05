package com.pollution.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.WindowAverage;
import com.pollution.common.thresholds.Thresholds;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every message and persisted value must survive the trip through JSON:
 * the messages travel that way between services, and the constructor
 * parameter names are what rebuilds them — a renamed parameter would break
 * deserialization silently anywhere but here.
 */
class JsonSupportTest {

    private static final Instant AT = Instant.parse("2026-09-05T10:00:00Z");
    private static final String SOURCE = "purpleair:Ganei-Ayalon";

    @Test
    void aReadingMakesTheRoundTrip() {
        PollutionData reading = new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 12.5, AT);

        assertEquals(reading, JsonSupport.fromJson(JsonSupport.toJson(reading), PollutionData.class));
        assertEquals(reading, JsonSupport.fromJson(JsonSupport.toJsonBytes(reading), PollutionData.class));
    }

    @Test
    void anAverageMakesTheRoundTrip() {
        PollutionAverage average = new PollutionAverage("Tel Aviv", SOURCE, Pollutant.PM2_5, List.of(
                new WindowAverage(Duration.ofMinutes(10), 12.5, 3),
                new WindowAverage(Duration.ofHours(1), 11.25, 18)), AT);

        assertEquals(average, JsonSupport.fromJson(JsonSupport.toJson(average), PollutionAverage.class));
    }

    @Test
    void anAlertMakesTheRoundTripWithAndWithoutAWindow() {
        PollutionAlert reading = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, null, 60, 50, AT);
        PollutionAlert hour = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, Duration.ofHours(1), 40, 31.25, AT);

        assertEquals(reading, JsonSupport.fromJson(JsonSupport.toJson(reading), PollutionAlert.class));
        assertEquals(hour, JsonSupport.fromJson(JsonSupport.toJson(hour), PollutionAlert.class));
    }

    @Test
    void thresholdsMakeTheRoundTripWithDurationsAsKeys() {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, 25.0);
        }
        Thresholds thresholds = new Thresholds(baselines, Map.of(Duration.ofHours(1), 1.25, Duration.ofMinutes(10), 1.35), 2);

        assertEquals(thresholds, JsonSupport.fromJson(JsonSupport.toJson(thresholds), Thresholds.class));
    }

    @Test
    void instantsAndDurationsAreWrittenAsIsoStrings() {
        PollutionAlert alert = new PollutionAlert("Tel Aviv", SOURCE, Pollutant.PM2_5, Duration.ofHours(1), 40, 31.25, AT);

        String json = JsonSupport.toJson(alert);

        assertTrue(json.contains("\"2026-09-05T10:00:00Z\""), json);
        assertTrue(json.contains("\"PT1H\""), json);
    }

    @Test
    void unknownPropertiesAreIgnored() {
        String json = "{\"city\":\"Tel Aviv\",\"source\":\"" + SOURCE + "\",\"pollutant\":\"PM2_5\","
                + "\"value\":12.5,\"timestamp\":\"2026-09-05T10:00:00Z\",\"someday\":true}";

        assertEquals(new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, 12.5, AT),
                JsonSupport.fromJson(json, PollutionData.class));
    }

    @Test
    void malformedJsonIsAJsonException() {
        assertThrows(JsonException.class, () -> JsonSupport.fromJson("{\"city\":", PollutionData.class));
    }

    @Test
    void aValueTheConstructorRejectsIsAJsonException() {
        String noSource = "{\"city\":\"Tel Aviv\",\"pollutant\":\"PM2_5\",\"value\":12.5,\"timestamp\":\"2026-09-05T10:00:00Z\"}";

        assertThrows(JsonException.class, () -> JsonSupport.fromJson(noSource, PollutionData.class));
    }
}
