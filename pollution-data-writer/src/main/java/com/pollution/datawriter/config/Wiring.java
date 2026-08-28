package com.pollution.datawriter.config;

import static com.pollution.common.config.Config.POLLUTION_ALERT_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_AVERAGE_TOPIC;
import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;

import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.JsonDeserializer;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;

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
}
