package com.pollution.dataanalyzer.config;

import static com.pollution.common.config.Config.POLLUTION_AVERAGE_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;
import static com.pollution.persistence.config.Config.getRedisHost;
import static com.pollution.persistence.config.Config.getRedisPort;

import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.JsonDeserializer;
import com.pollution.common.pubsub.kafka.JsonSerializer;
import com.pollution.common.pubsub.kafka.KafkaPublisher;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;
import com.pollution.dataanalyzer.PollutionDataAnalyzerService;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.persistence.CacheBackedRollingAverageStateStore;
import com.pollution.dataanalyzer.persistence.IRollingAverageStateStore;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.redis.RedisPollutionCache;

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

    public static IPublisher<PollutionAverage> createAveragePublisher() {
        return new KafkaPublisher<>(POLLUTION_AVERAGE_TOPIC, new JsonSerializer<>());
    }

    public static IRollingAverageStateStore createStateStore() {
        IPollutionCache<RollingAverageState> cache =
                new RedisPollutionCache<>(getRedisHost(), getRedisPort(), RollingAverageState.class);
        return new CacheBackedRollingAverageStateStore(cache, Config.ROLLING_AVERAGE_STATE_KEY_PREFIX);
    }

    public static PollutionDataAnalyzerService createAnalyzerService(ISubscriber<PollutionData> pollutionSubscriber,
                                                                     IPublisher<PollutionAverage> averagePublisher,
                                                                     IRollingAverageStateStore stateStore) {
        return new PollutionDataAnalyzerService(
                pollutionSubscriber, averagePublisher, stateStore, Config.getRollingAverageWindows());
    }
}
