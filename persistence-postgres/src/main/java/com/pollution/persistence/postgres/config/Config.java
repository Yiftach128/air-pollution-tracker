package com.pollution.persistence.postgres.config;

import com.pollution.common.config.Env;

/** Connection settings for the PostgreSQL-backed repositories; values only. */
public final class Config {

    private static final String DEFAULT_POSTGRES_HOST = "localhost";
    private static final int DEFAULT_POSTGRES_PORT = 5432;
    private static final String DEFAULT_POSTGRES_DB = "pollution";
    private static final String DEFAULT_POSTGRES_USER = "pollution";
    private static final String DEFAULT_POSTGRES_PASSWORD = "";
    private static final int DEFAULT_POSTGRES_POOL_SIZE = 4;

    private Config() {
    }

    public static String getPostgresHost() {
        return Env.getString("POSTGRES_HOST", DEFAULT_POSTGRES_HOST);
    }

    public static int getPostgresPort() {
        return Env.getInt("POSTGRES_PORT", DEFAULT_POSTGRES_PORT);
    }

    public static String getPostgresDb() {
        return Env.getString("POSTGRES_DB", DEFAULT_POSTGRES_DB);
    }

    public static String getPostgresUser() {
        return Env.getString("POSTGRES_USER", DEFAULT_POSTGRES_USER);
    }

    public static String getPostgresPassword() {
        return Env.getString("POSTGRES_PASSWORD", DEFAULT_POSTGRES_PASSWORD);
    }

    /** Most connections the pool keeps open to the database. */
    public static int getPostgresPoolSize() {
        return Env.getInt("POSTGRES_POOL_SIZE", DEFAULT_POSTGRES_POOL_SIZE);
    }

    public static String getPostgresJdbcUrl() {
        return "jdbc:postgresql://" + getPostgresHost() + ":" + getPostgresPort() + "/" + getPostgresDb();
    }
}
