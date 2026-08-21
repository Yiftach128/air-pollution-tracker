package com.pollution.datacollector.config;

import static com.pollution.common.config.Config.POLLUTION_DATA_TOPIC;

import com.pollution.common.pubsub.Producer;
import com.pollution.common.pubsub.kafka.KafkaProducer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.kafka.common.serialization.StringSerializer;

public final class Config {

    public static final String SERVICE_NAME = "pollution-data-collector";

    private static final int SCHEDULER_POOL_SIZE = 2;
    private static final long DEFAULT_PUBLISH_INTERVAL_MS = 5000;

    private Config() {
    }

    public static long getPublishIntervalMillis() {
        String env = System.getenv("PUBLISH_INTERVAL_MS");
        if (env != null) {
            return Long.parseLong(env);
        }
        return DEFAULT_PUBLISH_INTERVAL_MS;
    }

    public static Producer<String> createPollutionProducer() {
        return new KafkaProducer<>(POLLUTION_DATA_TOPIC, new StringSerializer());
    }

    public static ScheduledExecutorService createScheduler() {
        return Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE);
    }
}
