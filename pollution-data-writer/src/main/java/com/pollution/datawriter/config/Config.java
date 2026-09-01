package com.pollution.datawriter.config;

/**
 * The writer's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them. The settings of the stores the writer
 * shares with other services — how long a current reading stays current —
 * are not the writer's to declare; they live in
 * {@link com.pollution.persistence.config.Config}.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-data-writer";

    private Config() {
    }
}
