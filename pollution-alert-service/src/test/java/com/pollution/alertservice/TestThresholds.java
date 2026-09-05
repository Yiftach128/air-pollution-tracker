package com.pollution.alertservice;

import com.pollution.alertservice.detection.ThresholdDetector;
import com.pollution.common.entities.Pollutant;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * The thresholds the alert service's tests judge by, spelled out so every
 * expected value in a test can be worked out by hand: PM2.5 has a baseline
 * of 25, so a single reading alerts above 50, a 10-minute average above
 * 33.75, an hour's above 31.25 and a day's above 25. Every other pollutant
 * has a baseline of 100.
 */
public final class TestThresholds {

    public static final double PM25_BASELINE = 25;
    public static final double OTHER_BASELINE = 100;
    public static final double READING_FACTOR = 2;
    public static final Map<Duration, Double> WINDOW_FACTORS = Map.of(
            Duration.ofHours(24), 1.0,
            Duration.ofHours(1), 1.25,
            Duration.ofMinutes(10), 1.35);

    private TestThresholds() {
    }

    public static Map<Pollutant, Double> baselines() {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, pollutant == Pollutant.PM2_5 ? PM25_BASELINE : OTHER_BASELINE);
        }
        return baselines;
    }

    public static ThresholdDetector detector() {
        return new ThresholdDetector(baselines(), WINDOW_FACTORS, READING_FACTOR);
    }
}
