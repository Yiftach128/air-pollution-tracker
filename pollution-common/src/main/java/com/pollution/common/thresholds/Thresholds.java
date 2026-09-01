package com.pollution.common.thresholds;

import com.pollution.common.entities.Pollutant;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The concentrations above which a measurement is an exceedance, shared by
 * every service that judges one — the alert service raises alerts on them,
 * the API service shows the dashboard where they lie — so they cannot
 * disagree. Each pollutant has a baseline in its own unit (a 24-hour
 * guideline level) and each measurement length a factor on it: a 24-hour
 * average is compared with the baseline as it is, shorter windows with a
 * higher multiple, a single reading — a spike — with the highest, since the
 * shorter the measurement the more it swings.
 * <p>
 * Read from {@code thresholds.json} by {@link ThresholdsLoader}; immutable,
 * validated on construction.
 *
 * @param baselines     the baseline of every {@link Pollutant}, in that
 *                      pollutant's unit; each finite and positive
 * @param windowFactors the factor on the baseline for each rolling-average
 *                      window that has one; each finite and positive
 * @param readingFactor the factor on the baseline for a single reading;
 *                      finite and positive
 */
public record Thresholds(Map<Pollutant, Double> baselines, Map<Duration, Double> windowFactors, double readingFactor) {

    public Thresholds {
        Objects.requireNonNull(baselines, "baselines");
        Objects.requireNonNull(windowFactors, "windowFactors");
        EnumMap<Pollutant, Double> baselineCopy = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            Double baseline = baselines.get(pollutant);
            if (baseline == null) {
                throw new IllegalArgumentException("no threshold for " + pollutant);
            }
            requireFinitePositive("threshold for " + pollutant, baseline);
            baselineCopy.put(pollutant, baseline);
        }
        Map<Duration, Double> factorCopy = new HashMap<>();
        for (Map.Entry<Duration, Double> entry : windowFactors.entrySet()) {
            Duration window = Objects.requireNonNull(entry.getKey(), "window");
            Double factor = entry.getValue();
            if (factor == null) {
                throw new IllegalArgumentException("no threshold factor for window " + window);
            }
            requireFinitePositive("threshold factor for window " + window, factor);
            factorCopy.put(window, factor);
        }
        requireFinitePositive("threshold factor for single readings", readingFactor);
        baselines = Collections.unmodifiableMap(baselineCopy);
        windowFactors = Collections.unmodifiableMap(factorCopy);
    }

    /** The threshold of a single reading of the pollutant: its baseline times the reading factor. */
    public double readingThreshold(Pollutant pollutant) {
        return baselines.get(pollutant) * readingFactor;
    }

    /**
     * The threshold of an average of the pollutant over the window: its
     * baseline times the window's factor.
     *
     * @throws IllegalArgumentException if the window has no factor
     */
    public double windowThreshold(Pollutant pollutant, Duration window) {
        Double factor = windowFactors.get(Objects.requireNonNull(window, "window"));
        if (factor == null) {
            throw new IllegalArgumentException("no threshold factor for window " + window);
        }
        return baselines.get(pollutant) * factor;
    }

    private static void requireFinitePositive(String what, double value) {
        if (Double.isInfinite(value) || !(value > 0)) {
            throw new IllegalArgumentException(what + " must be finite and positive, was " + value);
        }
    }
}
