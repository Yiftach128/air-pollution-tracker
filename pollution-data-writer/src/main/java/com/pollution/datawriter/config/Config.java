package com.pollution.datawriter.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;

import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.pubsub.kafka.KafkaSubscriber;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class Config {

    public static final String SERVICE_NAME = "pollution-data-writer";

    private Config() {
    }

    public static ISubscriber<String> createPollutionSubscriber() {
        return new KafkaSubscriber<>(POLLUTION_DATA_TOPIC, SERVICE_NAME, new StringDeserializer());
    }
}
