package com.pollution.datacollector.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;
import static com.pollution.common.config.Config.getHealthPort;

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
import com.pollution.datacollector.api.purpleair.PurpleAirSensorApi;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.fetchers.purpleair.PurpleAirReadingsFetcher;
import com.pollution.datacollector.registry.ISensorRegistry;
import com.pollution.datacollector.registry.purpleair.PurpleAirSensorRegistry;
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
        PurpleAirSensorApi sensorApi = new PurpleAirSensorApi(
                http, Config.PURPLEAIR_BASE_URI, apiKeys, Config.PURPLEAIR_REQUEST_TIMEOUT);
        ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry =
                new PurpleAirSensorRegistry(sensorApi, Config.getSensors());
        return new PurpleAirReadingsFetcher(sensorApi, sensorRegistry);
    }

    public static IPublisher<PollutionData> createPollutionPublisher() {
        return new KafkaPublisher<>(POLLUTION_DATA_TOPIC, new JsonSerializer<>());
    }

    public static PollutionDataCollectorService createCollectorService(IReadingsFetcher readingsFetcher,
                                                                       IPublisher<PollutionData> publisher) {
        PollutionDataCollectorService.Schedule schedule = new PollutionDataCollectorService.Schedule(
                Config.getPollInterval(), Config.getPublishInterval(), Config.getReadingMaxAge());
        return new PollutionDataCollectorService(
                readingsFetcher,
                publisher,
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
