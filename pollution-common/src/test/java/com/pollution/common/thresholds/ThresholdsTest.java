package com.pollution.common.thresholds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.common.entities.Pollutant;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThresholdsTest {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private static Map<Pollutant, Double> baselinesOf(double value) {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, value);
        }
        return baselines;
    }

    @Test
    void theReadingThresholdIsTheBaselineTimesTheReadingFactor() {
        Thresholds thresholds = new Thresholds(baselinesOf(25), Map.of(HOUR, 1.25), 2);

        assertEquals(50, thresholds.readingThreshold(Pollutant.PM2_5));
    }

    @Test
    void aWindowThresholdIsTheBaselineTimesTheWindowsFactor() {
        Thresholds thresholds = new Thresholds(baselinesOf(25), Map.of(HOUR, 1.25), 2);

        assertEquals(31.25, thresholds.windowThreshold(Pollutant.PM2_5, HOUR));
    }

    @Test
    void aWindowWithoutAFactorHasNoThreshold() {
        Thresholds thresholds = new Thresholds(baselinesOf(25), Map.of(HOUR, 1.25), 2);

        assertThrows(IllegalArgumentException.class, () -> thresholds.windowThreshold(Pollutant.PM2_5, TEN_MINUTES));
    }

    @Test
    void everyPollutantNeedsABaseline() {
        Map<Pollutant, Double> onlyPm25 = Map.of(Pollutant.PM2_5, 25.0);

        assertThrows(IllegalArgumentException.class, () -> new Thresholds(onlyPm25, Map.of(), 2));
    }

    @Test
    void baselinesMustBeFiniteAndPositive() {
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Map<Pollutant, Double> baselines = baselinesOf(25);
            baselines.put(Pollutant.CO, bad);
            assertThrows(IllegalArgumentException.class, () -> new Thresholds(baselines, Map.of(), 2), "baseline " + bad);
        }
    }

    @Test
    void factorsMustBeFiniteAndPositive() {
        Map<Duration, Double> zeroFactor = Map.of(HOUR, 0.0);
        Map<Duration, Double> nullFactor = new HashMap<>();
        nullFactor.put(HOUR, null);

        assertThrows(IllegalArgumentException.class, () -> new Thresholds(baselinesOf(25), zeroFactor, 2));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(baselinesOf(25), nullFactor, 2));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(baselinesOf(25), Map.of(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(baselinesOf(25), Map.of(), 0));
    }

    @Test
    void theMapsCannotBeChangedAfterwards() {
        Map<Pollutant, Double> baselines = baselinesOf(25);
        Map<Duration, Double> factors = new HashMap<>(Map.of(HOUR, 1.25));
        Thresholds thresholds = new Thresholds(baselines, factors, 2);

        baselines.put(Pollutant.PM2_5, 1.0);
        factors.put(HOUR, 9.0);

        assertEquals(25, thresholds.baselines().get(Pollutant.PM2_5));
        assertEquals(1.25, thresholds.windowFactors().get(HOUR));
        assertThrows(UnsupportedOperationException.class, () -> thresholds.baselines().put(Pollutant.PM2_5, 1.0));
        assertThrows(UnsupportedOperationException.class, () -> thresholds.windowFactors().put(HOUR, 1.0));
    }
}
