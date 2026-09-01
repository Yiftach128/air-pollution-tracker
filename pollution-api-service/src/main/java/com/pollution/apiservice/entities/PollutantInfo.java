package com.pollution.apiservice.entities;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.thresholds.Thresholds;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A pollutant as the dashboard needs to know it: how the API names it, how
 * to show it and where its thresholds lie. Lets the page label values and
 * mark exceedances without a copy of {@link Pollutant} or of the
 * {@link Thresholds}.
 *
 * @param name        the API name, as it appears in readings ({@code PM2_5})
 * @param displayName the name to show ({@code PM2.5})
 * @param unit        the unit values are in
 * @param thresholds  the effective threshold of each measurement length, in
 *                    that unit: {@link #READING_THRESHOLD} for a single
 *                    reading and one per rolling-average window keyed by its
 *                    ISO-8601 length ({@code PT10M}, {@code PT1H},
 *                    {@code PT24H}), shortest first
 */
public record PollutantInfo(String name, String displayName, String unit, Map<String, Double> thresholds) {

    /** The key of the single-reading threshold. */
    public static final String READING_THRESHOLD = "raw";

    public PollutantInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(thresholds, "thresholds");
        thresholds = Collections.unmodifiableMap(new LinkedHashMap<>(thresholds));
    }

    /** The pollutant with its thresholds worked out: baseline times factor, per measurement length. */
    public static PollutantInfo of(Pollutant pollutant, Thresholds thresholds) {
        Map<String, Double> effective = new LinkedHashMap<>();
        effective.put(READING_THRESHOLD, thresholds.readingThreshold(pollutant));
        thresholds.windowFactors().keySet().stream().sorted().forEach(
                window -> effective.put(window.toString(), thresholds.windowThreshold(pollutant, window)));
        return new PollutantInfo(pollutant.name(), pollutant.displayName(), pollutant.unit(), effective);
    }
}
