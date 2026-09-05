package com.pollution.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pollution.apiservice.entities.HistoryLimits;
import com.pollution.apiservice.entities.PollutantInfo;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.json.JsonSupport;
import com.pollution.common.thresholds.Thresholds;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code /api/pollutants} and {@code /api/limits}: what the page is told before it asks for data. */
class CatalogueHandlersTest {

    private static final Request GET = new Request("GET", "/api/x", Map.of());

    private static Thresholds thresholds() {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, 25.0);
        }
        return new Thresholds(baselines, Map.of(Duration.ofHours(1), 1.25, Duration.ofMinutes(10), 1.35), 2);
    }

    @Test
    void everyPollutantIsListedInOrderWithItsThresholds() {
        Response response = new PollutantsHandler(thresholds()).handle(GET);

        PollutantInfo[] pollutants = JsonSupport.fromJson(response.bodyText(), PollutantInfo[].class);
        assertEquals(Response.OK, response.status());
        assertEquals(Arrays.stream(Pollutant.values()).map(Pollutant::name).toList(),
                Arrays.stream(pollutants).map(PollutantInfo::name).toList());
        assertEquals(Map.of("raw", 50.0, "PT10M", 33.75, "PT1H", 31.25), pollutants[0].thresholds());
    }

    @Test
    void thePollutantsAreWorkedOutOnceAndAnsweredTheSameEveryTime() {
        PollutantsHandler handler = new PollutantsHandler(thresholds());

        assertEquals(handler.handle(GET).bodyText(), handler.handle(GET).bodyText());
    }

    @Test
    void theLimitsAreToldInHoursAndDays() {
        Response response = new LimitsHandler(Duration.ofHours(24), Duration.ofDays(365)).handle(GET);

        assertEquals(Response.OK, response.status());
        assertEquals(new HistoryLimits(24, 365), JsonSupport.fromJson(response.bodyText(), HistoryLimits.class));
        assertEquals("{\"defaultHistoryHours\":24,\"maxHistoryDays\":365}", response.bodyText());
    }

    @Test
    void thresholdsAreKeyedShortestMeasurementFirst() {
        PollutantInfo info = PollutantInfo.of(Pollutant.PM2_5, thresholds());

        assertEquals(List.of("raw", "PT10M", "PT1H"), List.copyOf(info.thresholds().keySet()));
    }
}
