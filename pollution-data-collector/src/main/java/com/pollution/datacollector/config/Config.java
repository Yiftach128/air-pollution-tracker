package com.pollution.datacollector.config;

import com.pollution.common.config.Env;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * The collector's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-data-collector";

    public static final URI PURPLEAIR_BASE_URI = URI.create("https://api.purpleair.com/");
    public static final Duration PURPLEAIR_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration PURPLEAIR_REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** Thread names of the two collector loops, as they appear in logs. */
    public static final String POLL_THREAD_NAME = "purpleair-poll";
    public static final String PUBLISH_THREAD_NAME = "kafka-publish";

    /**
     * PurpleAir read keys, used round-robin so API points are spread across them.
     * Overridden by the {@code PURPLEAIR_API_KEYS} env var (comma-separated) when set.
     */
    private static final List<String> DEFAULT_PURPLEAIR_API_KEYS = List.of(
            "DCCF8399-9D98-11F1-9E30-4201AC1DC129"
    );

    /**
     * The sensors this instance follows, each as {@code <sensorIndex>=<city>}
     * (see {@link PurpleAirSensor#parse}). Overridden by the
     * {@code PURPLEAIR_SENSORS} env var (comma-separated) when set — which is
     * how several collectors share the sensors out: every instance gets its
     * own list, and the lists must not overlap, or a sensor is polled,
     * stored and averaged twice.
     */
    private static final List<String> DEFAULT_PURPLEAIR_SENSORS = List.of(
            "308702=Ganei Ayalon",
            "298123=Shoham"
    );

    /** How often PurpleAir is asked for fresh readings; each poll costs API points per sensor. */
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMinutes(5);
    /** How often the last reading of every sensor is (re)published. */
    private static final Duration DEFAULT_PUBLISH_INTERVAL = Duration.ofSeconds(10);
    /** A reading not refreshed for this long stops being republished; default is two missed polls. */
    private static final int DEFAULT_READING_MAX_AGE_IN_POLLS = 2;

    private Config() {
    }

    public static Duration getPollInterval() {
        return Duration.ofMillis(Env.getLong("POLL_INTERVAL_MS", DEFAULT_POLL_INTERVAL.toMillis()));
    }

    public static Duration getPublishInterval() {
        return Duration.ofMillis(Env.getLong("PUBLISH_INTERVAL_MS", DEFAULT_PUBLISH_INTERVAL.toMillis()));
    }

    public static Duration getReadingMaxAge() {
        Duration defaultMaxAge = getPollInterval().multipliedBy(DEFAULT_READING_MAX_AGE_IN_POLLS);
        return Duration.ofMillis(Env.getLong("READING_MAX_AGE_MS", defaultMaxAge.toMillis()));
    }

    public static List<String> getPurpleAirApiKeys() {
        return Env.getList("PURPLEAIR_API_KEYS", DEFAULT_PURPLEAIR_API_KEYS);
    }

    /**
     * The sensors to poll, in configured order.
     *
     * @throws IllegalArgumentException if an entry of {@code PURPLEAIR_SENSORS} is malformed
     */
    public static List<PurpleAirSensor> getSensors() {
        return Env.getList("PURPLEAIR_SENSORS", DEFAULT_PURPLEAIR_SENSORS).stream()
                .map(PurpleAirSensor::parse)
                .toList();
    }
}
