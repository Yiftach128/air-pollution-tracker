package com.pollution.alertservice.detection;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.WindowAverage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Decides which measurements are alerts. Each pollutant has a baseline
 * threshold — a 24-hour guideline level — and each measurement type a factor
 * on it: a 24-hour average is compared against the baseline as is, shorter
 * windows against a higher multiple of it, and a single reading (a spike)
 * against the highest, since the shorter the measurement the more it swings.
 * A value strictly above its threshold is an alert; a NaN never is.
 * <p>
 * Immutable, so safe to share between the subscriber threads. The alert's
 * city and timestamp are those of the measurement that triggered it; its
 * threshold is the effective one, baseline times factor.
 */
public final class ThresholdDetector {

    private static final Logger logger = PollutionLogger.getLogger(ThresholdDetector.class);

    /** The factor of a window nobody configured one for: the baseline itself. */
    private static final double UNCONFIGURED_WINDOW_FACTOR = 1.0;

    private final Map<Pollutant, Double> baselines;
    private final Map<Duration, Double> windowFactors;
    private final double readingFactor;
    private final Set<Duration> windowsWarnedAbout = ConcurrentHashMap.newKeySet();

    /**
     * @param baselines     the baseline threshold of every {@link Pollutant}, in
     *                      that pollutant's unit; each finite and positive
     * @param windowFactors the factor on the baseline for each rolling-average
     *                      window that has one; each finite and positive
     * @param readingFactor the factor on the baseline for a single reading;
     *                      finite and positive
     */
    public ThresholdDetector(Map<Pollutant, Double> baselines, Map<Duration, Double> windowFactors, double readingFactor) {
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
        this.baselines = Collections.unmodifiableMap(baselineCopy);
        this.windowFactors = Collections.unmodifiableMap(factorCopy);
        this.readingFactor = readingFactor;
    }

    private static void requireFinitePositive(String what, double value) {
        if (Double.isInfinite(value) || !(value > 0)) {
            throw new IllegalArgumentException(what + " must be finite and positive, was " + value);
        }
    }

    /**
     * The threshold a measurement is compared against, in the pollutant's
     * unit: the pollutant's baseline times the factor of the measurement type.
     *
     * @param window the rolling-average window, or {@code null} for a single reading
     */
    public double thresholdOf(Pollutant pollutant, Duration window) {
        return baselines.get(pollutant) * factorOf(window);
    }

    private double factorOf(Duration window) {
        if (window == null) {
            return readingFactor;
        }
        Double factor = windowFactors.get(window);
        if (factor == null) {
            if (windowsWarnedAbout.add(window)) {
                logger.warn("no threshold factor configured for window {}; using the baseline threshold (x{})",
                        window, UNCONFIGURED_WINDOW_FACTOR);
            }
            return UNCONFIGURED_WINDOW_FACTOR;
        }
        return factor;
    }

    /** At most one alert, with no window: the reading itself exceeded the single-reading threshold. */
    public List<PollutionAlert> detect(PollutionData reading) {
        double threshold = thresholdOf(reading.pollutant(), null);
        if (!(reading.value() > threshold)) {
            return List.of();
        }
        return List.of(new PollutionAlert(
                reading.city(), reading.source(), reading.pollutant(), null, reading.value(), threshold, reading.timestamp()));
    }

    /** One alert per window whose mean exceeded that window's threshold, in the order the averages were given. */
    public List<PollutionAlert> detect(PollutionAverage average) {
        List<PollutionAlert> alerts = new ArrayList<>();
        for (WindowAverage windowAverage : average.averages()) {
            double threshold = thresholdOf(average.pollutant(), windowAverage.window());
            if (windowAverage.averageValue() > threshold) {
                alerts.add(new PollutionAlert(
                        average.city(), average.source(), average.pollutant(), windowAverage.window(),
                        windowAverage.averageValue(), threshold, average.timestamp()));
            }
        }
        return List.copyOf(alerts);
    }
}
