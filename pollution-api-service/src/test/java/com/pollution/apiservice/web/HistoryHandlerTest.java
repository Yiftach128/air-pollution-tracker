package com.pollution.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.apiservice.entities.SourceHistory;
import com.pollution.apiservice.queries.HistoryQuery;
import com.pollution.apiservice.testing.StubPollutionRepository;
import com.pollution.common.json.JsonSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The parsing of {@code /api/history}'s parameters; what is asked of the repository shows what was understood. */
class HistoryHandlerTest {

    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration DEFAULT_RANGE = Duration.ofHours(24);

    private final StubPollutionRepository repository = new StubPollutionRepository();
    private final HistoryHandler handler = new HistoryHandler(new HistoryQuery(repository, DEFAULT_RANGE, Duration.ofDays(365)));

    private static Request get(Map<String, String> query) {
        return new Request("GET", "/api/history", query);
    }

    @Test
    void theSourceIsRequired() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> handler.handle(get(Map.of())));

        assertTrue(thrown.getMessage().contains("source"), thrown.getMessage());
    }

    @Test
    void theRangeAndBucketAreParsedFromIsoStrings() {
        Response response = handler.handle(get(Map.of(
                "source", SOURCE,
                "from", "2026-09-04T10:00:00Z",
                "to", "2026-09-05T10:00:00Z",
                "bucket", "PT1H")));

        assertEquals(Response.OK, response.status());
        assertEquals(SOURCE, repository.lastSource());
        assertEquals(Instant.parse("2026-09-04T10:00:00Z"), repository.lastFrom());
        assertEquals(Instant.parse("2026-09-05T10:00:00Z"), repository.lastTo());
        assertEquals(Duration.ofHours(1), repository.lastBucket());
        assertEquals(List.of("findAverages", "summarize"), repository.queries());
    }

    @Test
    void rawMeansEveryReadingWhateverItsCase() {
        handler.handle(get(Map.of("source", SOURCE, "bucket", "raw")));
        handler.handle(get(Map.of("source", SOURCE, "bucket", "RAW")));

        assertNull(repository.lastBucket());
        assertEquals(List.of("findReadings", "summarize", "findReadings", "summarize"), repository.queries());
    }

    @Test
    void whatIsLeftOutIsFilledInByTheQuery() {
        Instant before = Instant.now();

        Response response = handler.handle(get(Map.of("source", SOURCE)));

        SourceHistory history = JsonSupport.fromJson(response.bodyText(), SourceHistory.class);
        assertFalse(history.to().isBefore(before));
        assertEquals(history.to().minus(DEFAULT_RANGE), history.from());
        assertNull(history.bucket());
    }

    @Test
    void aMalformedInstantIsRejectedNamingTheParameter() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(get(Map.of("source", SOURCE, "from", "yesterday"))));

        assertTrue(thrown.getMessage().startsWith("from "), thrown.getMessage());
        assertEquals(List.of(), repository.queries());
    }

    @Test
    void aMalformedBucketIsRejectedNamingTheParameter() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(get(Map.of("source", SOURCE, "bucket", "hourly"))));

        assertTrue(thrown.getMessage().startsWith("bucket "), thrown.getMessage());
    }

    @Test
    void aBlankParameterCountsAsLeftOut() {
        handler.handle(get(Map.of("source", SOURCE, "bucket", " ", "from", "")));

        assertNull(repository.lastBucket());
        assertEquals(List.of("findReadings", "summarize"), repository.queries());
    }
}
