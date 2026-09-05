package com.pollution.alertservice.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.alertservice.TestThresholds;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.WindowAverage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThresholdDetectorTest {

    private static final Instant AT = Instant.parse("2026-09-05T10:00:00Z");
    private static final String CITY = "Tel Aviv";
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofHours(24);

    private final ThresholdDetector detector = TestThresholds.detector();

    private static PollutionData reading(double value) {
        return new PollutionData(CITY, SOURCE, Pollutant.PM2_5, value, AT);
    }

    private static PollutionAverage average(WindowAverage... averages) {
        return new PollutionAverage(CITY, SOURCE, Pollutant.PM2_5, List.of(averages), AT);
    }

    private static WindowAverage over(Duration window, double mean) {
        return new WindowAverage(window, mean, 6);
    }

    private static PollutionAlert alert(Duration window, double value, double threshold) {
        return new PollutionAlert(CITY, SOURCE, Pollutant.PM2_5, window, value, threshold, AT);
    }

    @Test
    void aReadingStrictlyAboveTheReadingThresholdIsAnAlert() {
        assertEquals(List.of(alert(null, 50.5, 50)), detector.detect(reading(50.5)));
    }

    @Test
    void aReadingAtOrBelowTheReadingThresholdIsNot() {
        assertEquals(List.of(), detector.detect(reading(50)));
        assertEquals(List.of(), detector.detect(reading(49.9)));
    }

    @Test
    void aReadingThatIsNotANumberIsNeverAnAlert() {
        assertEquals(List.of(), detector.detect(reading(Double.NaN)));
    }

    @Test
    void eachWindowIsJudgedByItsOwnThresholdInTheOrderGiven() {
        PollutionAverage average = average(over(TEN_MINUTES, 34), over(HOUR, 32), over(DAY, 26));

        assertEquals(List.of(
                alert(TEN_MINUTES, 34, 33.75),
                alert(HOUR, 32, 31.25),
                alert(DAY, 26, 25)), detector.detect(average));
    }

    @Test
    void onlyTheWindowsAboveTheirThresholdAlert() {
        PollutionAverage average = average(over(TEN_MINUTES, 33.75), over(HOUR, 32), over(DAY, 24));

        assertEquals(List.of(alert(HOUR, 32, 31.25)), detector.detect(average));
    }

    @Test
    void aWindowNobodyConfiguredAFactorForIsJudgedByTheBaseline() {
        Duration twoHours = Duration.ofHours(2);

        assertEquals(List.of(alert(twoHours, 25.5, 25)), detector.detect(average(over(twoHours, 25.5))));
        assertEquals(List.of(), detector.detect(average(over(twoHours, 25))));
    }

    @Test
    void theThresholdIsTheBaselineTimesTheFactorOfTheMeasurement() {
        assertEquals(50, detector.thresholdOf(Pollutant.PM2_5, null));
        assertEquals(33.75, detector.thresholdOf(Pollutant.PM2_5, TEN_MINUTES));
        assertEquals(31.25, detector.thresholdOf(Pollutant.PM2_5, HOUR));
        assertEquals(25, detector.thresholdOf(Pollutant.PM2_5, DAY));
        assertEquals(200, detector.thresholdOf(Pollutant.CO, null));
    }

    @Test
    void everyPollutantNeedsABaseline() {
        Map<Pollutant, Double> onlyPm25 = Map.of(Pollutant.PM2_5, 25.0);

        assertThrows(IllegalArgumentException.class,
                () -> new ThresholdDetector(onlyPm25, TestThresholds.WINDOW_FACTORS, 2));
    }

    @Test
    void baselinesAndFactorsMustBeFiniteAndPositive() {
        Map<Pollutant, Double> zeroBaseline = TestThresholds.baselines();
        zeroBaseline.put(Pollutant.O3, 0.0);
        Map<Duration, Double> nanFactor = new HashMap<>(Map.of(HOUR, Double.NaN));

        assertThrows(IllegalArgumentException.class,
                () -> new ThresholdDetector(zeroBaseline, TestThresholds.WINDOW_FACTORS, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ThresholdDetector(TestThresholds.baselines(), nanFactor, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ThresholdDetector(TestThresholds.baselines(), TestThresholds.WINDOW_FACTORS, -1));
    }
}
