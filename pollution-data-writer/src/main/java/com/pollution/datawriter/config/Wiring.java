package com.pollution.datawriter.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;

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
}
