package com.pollution.datacollector.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;
import static com.pollution.common.config.Config.getHealthPort;
import static com.pollution.persistence.redis.config.Config.getRedisHost;
import static com.pollution.persistence.redis.config.Config.getRedisPort;

import com.pollution.common.entities.PollutionData;
import com.pollution.common.health.IHealthServer;
import com.pollution.common.health.NoopHealthServer;
import com.pollution.common.health.jdk.JdkHealthServer;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.kafka.JsonSerializer;
import com.pollution.common.pubsub.kafka.KafkaPublisher;
import com.pollution.datacollector.PollutionDataCollectorService;
import com.pollution.datacollector.api.IApiKeyProvider;
import com.pollution.datacollector.api.RoundRobinApiKeyProvider;
import com.pollution.datacollector.api.purpleair.IPurpleAirSensorApi;
import com.pollution.datacollector.api.purpleair.PurpleAirSensorApi;
import com.pollution.datacollector.entities.Member;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.fetchers.purpleair.PurpleAirReadingsFetcher;
import com.pollution.datacollector.membership.IGroupMembership;
import com.pollution.datacollector.membership.LeaseGroupMembership;
import com.pollution.datacollector.persistence.CacheBackedMembershipStore;
import com.pollution.datacollector.persistence.IMembershipStore;
import com.pollution.datacollector.registry.ISensorRegistry;
import com.pollution.datacollector.registry.purpleair.PurpleAirSensorRegistry;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.redis.RedisPollutionCache;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Assembles the collector from its {@link Config} values. This is the only
 * place in the service that names concrete implementations; everything it
 * returns is handed out as an interface.
 */
public final class Wiring {

    private Wiring() {
    }

    /** The answers to a container platform's probes: a real server when {@code HEALTH_PORT} is set, else nothing. */
    public static IHealthServer createHealthServer() {
        return getHealthPort().isPresent() ? new JdkHealthServer(getHealthPort().getAsInt()) : new NoopHealthServer();
    }

    public static IReadingsFetcher createReadingsFetcher() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Config.PURPLEAIR_CONNECT_TIMEOUT)
                .build();
        IApiKeyProvider apiKeys = new RoundRobinApiKeyProvider(Config.getPurpleAirApiKeys());
        IPurpleAirSensorApi sensorApi = new PurpleAirSensorApi(
                http, Config.PURPLEAIR_BASE_URI, apiKeys, Config.PURPLEAIR_REQUEST_TIMEOUT);
        ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry =
                new PurpleAirSensorRegistry(sensorApi, Config.getSensors());
        return new PurpleAirReadingsFetcher(sensorApi, sensorRegistry);
    }

    public static IPublisher<PollutionData> createPollutionPublisher() {
        return new KafkaPublisher<>(POLLUTION_DATA_TOPIC, new JsonSerializer<>());
    }

    /**
     * This instance's place in the group of running collectors: a lease in
     * Redis, renewed by heartbeat, from which every instance works out its
     * share of the sensors.
     */
    public static IGroupMembership createGroupMembership() {
        Clock clock = Clock.systemUTC();
        IPollutionCache<Member> cache = new RedisPollutionCache<>(getRedisHost(), getRedisPort(), Member.class);
        IMembershipStore store = new CacheBackedMembershipStore(cache, Config.MEMBER_KEY_PREFIX);
        return new LeaseGroupMembership(
                store,
                new Member(Config.getCollectorId(), clock.instant()),
                Config.getHeartbeatInterval(),
                Config.getLease(),
                singleThreadScheduler(Config.MEMBERSHIP_THREAD_NAME),
                clock);
    }

    public static PollutionDataCollectorService createCollectorService(IReadingsFetcher readingsFetcher,
                                                                       IPublisher<PollutionData> publisher,
                                                                       IGroupMembership membership) {
        PollutionDataCollectorService.Schedule schedule = new PollutionDataCollectorService.Schedule(
                Config.getPollInterval(), Config.getPublishInterval(), Config.getReadingMaxAge());
        return new PollutionDataCollectorService(
                readingsFetcher,
                publisher,
                membership,
                singleThreadScheduler(Config.POLL_THREAD_NAME),
                singleThreadScheduler(Config.PUBLISH_THREAD_NAME),
                schedule,
                Clock.systemUTC());
    }

    private static ScheduledExecutorService singleThreadScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(false);
            return thread;
        });
    }
}
