package com.pollution.dataanalyzer.config;

import com.pollution.common.config.Env;
import java.time.Duration;

/**
 * The analyzer's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-data-analyzer";

    /** Thread name of the snapshot persistence loop, as it appears in logs. */
    public static final String PERSIST_THREAD_NAME = "snapshot-persist";

    /** How far back each sensor's rolling average reaches. */
    private static final Duration DEFAULT_ROLLING_AVERAGE_WINDOW = Duration.ofMinutes(60);
    /** How often snapshots are persisted; bounds how much of a window a crash can lose. */
    private static final Duration DEFAULT_PERSIST_INTERVAL = Duration.ofMinutes(1);

    private Config() {
    }

    public static Duration getRollingAverageWindow() {
        return Duration.ofMillis(Env.getLong("ROLLING_AVERAGE_WINDOW_MS", DEFAULT_ROLLING_AVERAGE_WINDOW.toMillis()));
    }

    public static Duration getPersistInterval() {
        return Duration.ofMillis(Env.getLong("PERSIST_INTERVAL_MS", DEFAULT_PERSIST_INTERVAL.toMillis()));
    }
}
