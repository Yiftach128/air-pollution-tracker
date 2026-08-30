package com.pollution.datawriter.config;

import com.pollution.common.config.Env;
import java.time.Duration;

/**
 * The writer's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-data-writer";

    private static final long DEFAULT_LATEST_READING_TTL_MINUTES = 60;

    private Config() {
    }

    /**
     * How long a reading stays a series' current one when no newer reading
     * replaces it. After that the series has no current reading until it
     * reports again. Defaults to an hour: well past the collector's poll
     * interval, so a working sensor never expires, while a dead one drops
     * out within the hour.
     */
    public static Duration getLatestReadingTtl() {
        return Duration.ofMinutes(Env.getLong("LATEST_READING_TTL_MINUTES", DEFAULT_LATEST_READING_TTL_MINUTES));
    }
}
