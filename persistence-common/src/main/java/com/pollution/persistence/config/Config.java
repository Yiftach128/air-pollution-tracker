package com.pollution.persistence.config;

import com.pollution.common.config.Env;
import java.time.Duration;

/**
 * Settings of the stores shared between services; values only. They live
 * here, next to the stores, because a setting of a shared store is part of
 * its contract — like its key layout — and must not be declared differently
 * by the services that use it.
 */
public final class Config {

    private static final long DEFAULT_LATEST_READING_TTL_MINUTES = 60;

    private Config() {
    }

    /**
     * How long a reading stays a series' current one in the
     * {@link com.pollution.persistence.ILatestReadingStore} when no newer
     * reading replaces it. After that the series has no current reading
     * until it reports again. Defaults to an hour: well past the collector's
     * poll interval, so a working sensor never expires, while a dead one
     * drops out within the hour.
     */
    public static Duration getLatestReadingTtl() {
        return Duration.ofMinutes(Env.getLong("LATEST_READING_TTL_MINUTES", DEFAULT_LATEST_READING_TTL_MINUTES));
    }
}
