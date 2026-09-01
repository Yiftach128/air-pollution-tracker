package com.pollution.apiservice.web;

import com.pollution.apiservice.queries.SourceOverviewQuery;
import java.util.Objects;

/** {@code GET /api/sources}: every source with its current readings, as JSON. */
public final class SourcesHandler implements IRequestHandler {

    private final SourceOverviewQuery overview;

    public SourcesHandler(SourceOverviewQuery overview) {
        this.overview = Objects.requireNonNull(overview, "overview");
    }

    @Override
    public Response handle(Request request) {
        return Response.json(overview.overview());
    }
}
