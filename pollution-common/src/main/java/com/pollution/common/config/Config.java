package com.pollution.common.config;

public final class Config {

    public static final String POLLUTION_DATA_TOPIC = "pollution-data";

    private static final String DEFAULT_KAFKA_HOST = "localhost";
    private static final int DEFAULT_KAFKA_PORT = 9092;

    private Config() {
    }

    public static String getKafkaHost() {
        String env = System.getenv("KAFKA_HOST");
        if (env != null) {
            return env;
        }
        return DEFAULT_KAFKA_HOST;
    }

    public static int getKafkaPort() {
        String env = System.getenv("KAFKA_PORT");
        if (env != null) {
            return Integer.parseInt(env);
        }
        return DEFAULT_KAFKA_PORT;
    }

    public static String getKafkaBootstrapServers() {
        return getKafkaHost() + ":" + getKafkaPort();
    }
}
