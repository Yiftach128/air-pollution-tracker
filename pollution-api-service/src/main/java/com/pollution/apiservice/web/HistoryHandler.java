package com.pollution.apiservice.web;

import com.pollution.apiservice.queries.HistoryQuery;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * {@code GET /api/history?source=…[&from=…][&to=…][&bucket=…]}: one
 * source's history as JSON. {@code from} and {@code to} are ISO-8601
 * instants ({@code 2026-08-30T10:00:00Z}); {@code bucket} is an ISO-8601
 * duration ({@code PT1H}) to average over, or {@code raw} (the default)
 * for every reading as it is. What is left out is filled in by the query.
 */
public final class HistoryHandler implements IRequestHandler {

    private static final String RAW_BUCKET = "raw";

    private final HistoryQuery history;

    public HistoryHandler(HistoryQuery history) {
        this.history = Objects.requireNonNull(history, "history");
    }

    @Override
    public Response handle(Request request) {
        String source = request.requiredParam("source");
        Instant from = request.param("from").map(value -> parseInstant("from", value)).orElse(null);
        Instant to = request.param("to").map(value -> parseInstant("to", value)).orElse(null);
        Duration bucket = request.param("bucket")
                .filter(value -> !value.equalsIgnoreCase(RAW_BUCKET))
                .map(value -> parseDuration("bucket", value))
                .orElse(null);
        return Response.json(history.history(source, from, to, bucket));
    }

    private static Instant parseInstant(String name, String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 instant such as 2026-08-30T10:00:00Z, was " + value);
        }
    }

    private static Duration parseDuration(String name, String value) {
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be raw or an ISO-8601 duration such as PT1H, was " + value);
        }
    }
}
