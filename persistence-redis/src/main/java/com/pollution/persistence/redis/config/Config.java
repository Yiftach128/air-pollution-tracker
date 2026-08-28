package com.pollution.persistence.redis.config;

import com.pollution.common.config.Env;

/** Connection settings for the Redis-backed cache; values only. */
public final class Config {

    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;

    private Config() {
    }

    public static String getRedisHost() {
        return Env.getString("REDIS_HOST", DEFAULT_REDIS_HOST);
    }

    public static int getRedisPort() {
        return Env.getInt("REDIS_PORT", DEFAULT_REDIS_PORT);
    }
}
