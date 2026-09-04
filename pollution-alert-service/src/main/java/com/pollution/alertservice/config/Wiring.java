package com.pollution.alertservice.config;

import static com.pollution.common.config.Config.POLLUTION_ALERT_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_AVERAGE_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;
import static com.pollution.persistence.redis.config.Config.getRedisHost;
import static com.pollution.persistence.redis.config.Config.getRedisPort;

import com.pollution.alertservice.PollutionAlertService;
import com.pollution.alertservice.detection.ThresholdDetector;
import com.pollution.alertservice.persistence.AlertCooldowns;
import com.pollution.alertservice.persistence.CacheBackedAlertCooldownStore;
import com.pollution.alertservice.persistence.IAlertCooldownStore;
import com.pollution.alertservice.senders.IAlertSender;
import com.pollution.alertservice.senders.LoggingAlertSender;
import com.pollution.alertservice.senders.telegram.TelegramAlertSender;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.JsonDeserializer;
import com.pollution.common.pubsub.kafka.JsonSerializer;
import com.pollution.common.pubsub.kafka.KafkaPublisher;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;
import com.pollution.common.thresholds.Thresholds;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.redis.RedisPollutionCache;

/**
 * Assembles the alert service from its {@link Config} values. This is the
 * only place in the service that names concrete implementations; everything
 * it returns is handed out as an interface.
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

    public static IPublisher<PollutionAlert> createAlertPublisher() {
        return new KafkaPublisher<>(POLLUTION_ALERT_TOPIC, new JsonSerializer<>());
    }

    /** @param thresholds the thresholds: each window they have a factor for gets a cooldown */
    public static IAlertCooldownStore createCooldownStore(Thresholds thresholds) {
        IPollutionCache<PollutionAlert> cache =
                new RedisPollutionCache<>(getRedisHost(), getRedisPort(), PollutionAlert.class);
        AlertCooldowns cooldowns = new AlertCooldowns(
                Config.getWindowCooldowns(thresholds.windowFactors().keySet()), Config.getReadingCooldown());
        return new CacheBackedAlertCooldownStore(cache, Config.LAST_SENT_KEY_PREFIX, cooldowns);
    }

    /**
     * The channel alerts are delivered over: the Telegram channel when a bot
     * token and chat id are configured, otherwise the log-only stand-in.
     * Either way the service logs every alert it raises.
     */
    public static IAlertSender createAlertSender() {
        if (Config.isTelegramConfigured()) {
            return new TelegramAlertSender(
                    Config.getTelegramApiBaseUrl(),
                    Config.getTelegramBotToken(),
                    Config.getTelegramChatId(),
                    Config.getTelegramTimeout());
        }
        return new LoggingAlertSender();
    }

    public static PollutionAlertService createAlertService(ISubscriber<PollutionData> pollutionSubscriber,
                                                           ISubscriber<PollutionAverage> averageSubscriber,
                                                           IPublisher<PollutionAlert> alertPublisher,
                                                           IAlertCooldownStore cooldownStore,
                                                           IAlertSender alertSender,
                                                           Thresholds thresholds) {
        ThresholdDetector detector = new ThresholdDetector(
                thresholds.baselines(), thresholds.windowFactors(), thresholds.readingFactor());
        return new PollutionAlertService(
                pollutionSubscriber, averageSubscriber, detector, cooldownStore, alertSender, alertPublisher);
    }
}
