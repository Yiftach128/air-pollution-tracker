package com.pollution.datawriter.config;

import static com.pollution.common.config.Config.POLLUTION_ALERT_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_AVERAGE_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;
import static com.pollution.persistence.config.Config.getLatestReadingTtl;
import static com.pollution.persistence.postgres.config.Config.getPostgresJdbcUrl;
import static com.pollution.persistence.postgres.config.Config.getPostgresPassword;
import static com.pollution.persistence.postgres.config.Config.getPostgresPoolSize;
import static com.pollution.persistence.postgres.config.Config.getPostgresUser;
import static com.pollution.persistence.redis.config.Config.getRedisHost;
import static com.pollution.persistence.redis.config.Config.getRedisPort;

import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.JsonDeserializer;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;
import com.pollution.datawriter.PollutionDataWriterService;
import com.pollution.persistence.CacheBackedLatestReadingStore;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.postgres.PostgresPollutionRepository;
import com.pollution.persistence.redis.RedisPollutionCache;

/**
 * Assembles the writer from its {@link Config} values. This is the only
 * place in the service that names concrete implementations; everything it
 * returns is handed out as an interface.
 */
public final class Wiring {

    private Wiring() {
    }

    public static ISubscriber<PollutionData> createPollutionSubscriber() {
        return new KafkaSubscriber<>(POLLUTION_DATA_TOPIC, Config.SERVICE_NAME, new JsonDeserializer<>(PollutionData.class));
    }

    public static ISubscriber<PollutionAverage> createAverageSubscriber() {
        return new KafkaSubscriber<>(POLLUTION_AVERAGE_TOPIC, Config.SERVICE_NAME, new JsonDeserializer<>(PollutionAverage.class));
    }

    public static ISubscriber<PollutionAlert> createAlertSubscriber() {
        return new KafkaSubscriber<>(POLLUTION_ALERT_TOPIC, Config.SERVICE_NAME, new JsonDeserializer<>(PollutionAlert.class));
    }

    /** The store readings go to; connects on creation. */
    public static IPollutionRepository createPollutionRepository() {
        return new PostgresPollutionRepository(
                getPostgresJdbcUrl(), getPostgresUser(), getPostgresPassword(), getPostgresPoolSize());
    }

    /** The store each series' current reading goes to, for fast lookups. */
    public static ILatestReadingStore createLatestReadingStore() {
        IPollutionCache<PollutionData> cache =
                new RedisPollutionCache<>(getRedisHost(), getRedisPort(), PollutionData.class);
        return new CacheBackedLatestReadingStore(cache, getLatestReadingTtl());
    }

    public static PollutionDataWriterService createWriterService(ISubscriber<PollutionData> pollutionSubscriber,
                                                                 ISubscriber<PollutionAverage> averageSubscriber,
                                                                 ISubscriber<PollutionAlert> alertSubscriber,
                                                                 IPollutionRepository repository,
                                                                 ILatestReadingStore latestReadings) {
        return new PollutionDataWriterService(
                pollutionSubscriber, averageSubscriber, alertSubscriber, repository, latestReadings);
    }
}
