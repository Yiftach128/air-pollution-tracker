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

    /** Thread names of the collector's loops, as they appear in logs. */
    public static final String POLL_THREAD_NAME = "purpleair-poll";
    public static final String PUBLISH_THREAD_NAME = "kafka-publish";
    public static final String MEMBERSHIP_THREAD_NAME = "group-membership";

    /**
     * Key prefix of the collector group's registrations in the cache: one
     * key per running instance, {@code collector:member:<id>}, alive while
     * its lease is (see {@link #getLease()}).
     */
    public static final String MEMBER_KEY_PREFIX = "collector:member:";

    /**
     * This instance's id in the collector group unless {@code COLLECTOR_ID}
     * says otherwise: {@code <hostname>-<pid>}. Unique among the processes of
     * one machine, and the same again when a container restarts — its
     * hostname (a pod's name) and pid do not change — so the restarted
     * instance replaces its old registration instead of standing beside it
     * until the lease runs out. Read once: the id must not change while the
     * process runs.
     */
    private static final String DEFAULT_COLLECTOR_ID =
            Env.getString("HOSTNAME", Env.getString("COMPUTERNAME", "collector")) + "-" + ProcessHandle.current().pid();
    /** How often an instance renews its lease and re-reads the group; a change of group shows within about this long. */
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    /** How long a registration outlives its last heartbeat: a crashed instance's sensors go unpolled for about this long. */
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    /**
     * Every sensor the collectors poll between them, each as
     * {@code <sensorIndex>=<city>} (see {@link PurpleAirSensor#parse}).
     * Overridden by the {@code PURPLEAIR_SENSORS} env var (comma-separated)
     * when set. The same list on every instance: which of them an instance
     * polls is its {@link com.pollution.datacollector.entities.Shard}, worked
     * out from the group membership, not configured.
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

    /**
     * The PurpleAir read keys, used round-robin so API points are spread
     * across them: {@code PURPLEAIR_API_KEYS}, comma-separated. A secret,
     * so there is no compiled default — it comes from the environment, the
     * {@code .env} file or the platform's secret store.
     *
     * @throws IllegalStateException if none is set
     */
    public static List<String> getPurpleAirApiKeys() {
        List<String> keys = Env.getList("PURPLEAIR_API_KEYS", List.of());
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "PURPLEAIR_API_KEYS must be set: at least one PurpleAir read key, comma-separated");
        }
        return keys;
    }

    /**
     * Every sensor the group polls, in configured order — the same on every instance.
     *
     * @throws IllegalArgumentException if an entry of {@code PURPLEAIR_SENSORS} is malformed
     */
    public static List<PurpleAirSensor> getSensors() {
        return Env.getList("PURPLEAIR_SENSORS", DEFAULT_PURPLEAIR_SENSORS).stream()
                .map(PurpleAirSensor::parse)
                .toList();
    }

    /** This instance's id in the collector group; see {@link #DEFAULT_COLLECTOR_ID}. */
    public static String getCollectorId() {
        return Env.getString("COLLECTOR_ID", DEFAULT_COLLECTOR_ID);
    }

    public static Duration getHeartbeatInterval() {
        return Duration.ofMillis(Env.getLong("COLLECTOR_HEARTBEAT_MS", DEFAULT_HEARTBEAT_INTERVAL.toMillis()));
    }

    /** How long an instance stays in the group without a heartbeat; must be at least two heartbeats. */
    public static Duration getLease() {
        return Duration.ofMillis(Env.getLong("COLLECTOR_LEASE_MS", DEFAULT_LEASE.toMillis()));
    }
}
