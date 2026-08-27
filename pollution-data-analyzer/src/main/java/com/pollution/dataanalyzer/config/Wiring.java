package com.pollution.dataanalyzer.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;

import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.JsonDeserializer;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;
import com.pollution.dataanalyzer.PollutionDataAnalyzerService;
import com.pollution.dataanalyzer.persistence.ISnapshotStore;
import com.pollution.dataanalyzer.persistence.LoggingSnapshotStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Assembles the analyzer from its {@link Config} values. This is the only
 * place in the service that names concrete implementations; everything it
 * returns is handed out as an interface.
 */
public final class Wiring {

    private Wiring() {
    }

    public static ISubscriber<PollutionData> createPollutionSubscriber() {
        return new KafkaSubscriber<>(POLLUTION_DATA_TOPIC, Config.SERVICE_NAME, new JsonDeserializer<>(PollutionData.class));
    }

    public static ISnapshotStore createSnapshotStore() {
        return new LoggingSnapshotStore();
    }

    public static PollutionDataAnalyzerService createAnalyzerService(ISubscriber<PollutionData> pollutionSubscriber,
                                                                     ISnapshotStore snapshotStore) {
        PollutionDataAnalyzerService.Settings settings = new PollutionDataAnalyzerService.Settings(
                Config.getRollingAverageWindow(), Config.getPersistInterval());
        return new PollutionDataAnalyzerService(
                pollutionSubscriber,
                snapshotStore,
                singleThreadScheduler(Config.PERSIST_THREAD_NAME),
                settings);
    }

    private static ScheduledExecutorService singleThreadScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(false);
            return thread;
        });
    }
}
