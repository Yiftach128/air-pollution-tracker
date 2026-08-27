package com.pollution.dataanalyzer.config;

import com.pollution.common.config.Env;
import java.time.Duration;
import java.util.List;

/**
 * The analyzer's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-data-analyzer";

    /** Cache key prefix for a series' rolling-average state; the sensor id and pollutant are appended. */
    public static final String ROLLING_AVERAGE_STATE_KEY_PREFIX = "analyzer:rolling-average-state:";

    /** Window lengths, in minutes, of the rolling averages kept for every sensor and pollutant. */
    private static final List<String> DEFAULT_ROLLING_AVERAGE_WINDOWS_MINUTES = List.of("10", "60", "1440");

    private Config() {
    }

    public static List<Duration> getRollingAverageWindows() {
        return Env.getList("ROLLING_AVERAGE_WINDOWS_MINUTES", DEFAULT_ROLLING_AVERAGE_WINDOWS_MINUTES).stream()
                .map(minutes -> Duration.ofMinutes(Long.parseLong(minutes)))
                .toList();
    }
}
