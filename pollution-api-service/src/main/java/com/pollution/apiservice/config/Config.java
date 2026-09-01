package com.pollution.apiservice.config;

import static com.pollution.common.config.Config.getThresholdsFile;

import com.pollution.common.config.Env;
import com.pollution.common.thresholds.Thresholds;
import com.pollution.common.thresholds.ThresholdsLoader;
import java.time.Duration;

/**
 * The API service's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-api-service";

    /** Where the static files of the dashboard live on the classpath. */
    public static final String STATIC_RESOURCE_ROOT = "static";

    /** Threads answering requests at once; a dashboard has a handful of viewers. */
    public static final int SERVER_THREADS = 4;

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    private static final long DEFAULT_HISTORY_HOURS = 24;
    private static final long DEFAULT_MAX_HISTORY_DAYS = 365;

    private Config() {
    }

    /** The TCP port the dashboard and the JSON API are served on. */
    public static int getPort() {
        return Env.getInt("API_PORT", DEFAULT_PORT);
    }

    /**
     * The address the server listens on. Defaults to the loopback address,
     * so the dashboard is reachable only from this machine; set
     * {@code 0.0.0.0} to serve it to the network.
     */
    public static String getBindAddress() {
        return Env.getString("API_BIND_ADDRESS", DEFAULT_BIND_ADDRESS);
    }

    /** How far back a history request reaches when it does not say. */
    public static Duration getDefaultHistoryRange() {
        return Duration.ofHours(Env.getLong("API_DEFAULT_HISTORY_HOURS", DEFAULT_HISTORY_HOURS));
    }

    /** The longest time range a single history request may ask for. */
    public static Duration getMaxHistoryRange() {
        return Duration.ofDays(Env.getLong("API_MAX_HISTORY_DAYS", DEFAULT_MAX_HISTORY_DAYS));
    }

    /**
     * The thresholds shared with the alert service, from {@code thresholds.json}
     * in pollution-common — or the file {@code THRESHOLDS_FILE} names — so the
     * dashboard marks as exceeding exactly what the alert service alerts on.
     */
    public static Thresholds getThresholds() {
        return getThresholdsFile().map(ThresholdsLoader::load).orElseGet(ThresholdsLoader::load);
    }
}
