package com.pollution.apiservice.web;

import com.pollution.apiservice.entities.PollutantInfo;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.thresholds.Thresholds;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** {@code GET /api/pollutants}: every pollutant the system knows, with its display name, unit and thresholds. */
public final class PollutantsHandler implements IRequestHandler {

    private final List<PollutantInfo> pollutants;

    /** @param thresholds the thresholds the pollutants' values are judged by */
    public PollutantsHandler(Thresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        this.pollutants = Arrays.stream(Pollutant.values()).map(pollutant -> PollutantInfo.of(pollutant, thresholds)).toList();
    }

    @Override
    public Response handle(Request request) {
        return Response.json(pollutants);
    }
}
