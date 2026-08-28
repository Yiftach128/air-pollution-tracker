package com.pollution.common.config;

public final class Config {

    public static final String POLLUTION_DATA_TOPIC = "pollution-data";
    public static final String POLLUTION_AVERAGE_TOPIC = "pollution-average";
    public static final String POLLUTION_ALERT_TOPIC = "pollution-alert";

    private static final String DEFAULT_KAFKA_HOST = "localhost";
    private static final int DEFAULT_KAFKA_PORT = 9092;

    private Config() {
    }

    public static String getKafkaHost() {
        return Env.getString("KAFKA_HOST", DEFAULT_KAFKA_HOST);
    }

    public static int getKafkaPort() {
        return Env.getInt("KAFKA_PORT", DEFAULT_KAFKA_PORT);
    }

    public static String getKafkaBootstrapServers() {
        return getKafkaHost() + ":" + getKafkaPort();
    }
}
