package com.pollution.apiservice.web;

import com.pollution.apiservice.entities.HistoryLimits;
import java.time.Duration;
import java.util.Objects;

/** {@code GET /api/limits}: the bounds on history requests, so the page validates before asking. */
public final class LimitsHandler implements IRequestHandler {

    private final HistoryLimits limits;

    /**
     * @param defaultRange how far back a history reaches when the request does not say
     * @param maxRange     the longest range one request may ask for
     */
    public LimitsHandler(Duration defaultRange, Duration maxRange) {
        Objects.requireNonNull(defaultRange, "defaultRange");
        Objects.requireNonNull(maxRange, "maxRange");
        this.limits = new HistoryLimits(defaultRange.toHours(), maxRange.toDays());
    }

    @Override
    public Response handle(Request request) {
        return Response.json(limits);
    }
}
