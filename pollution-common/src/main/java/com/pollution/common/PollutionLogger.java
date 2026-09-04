package com.pollution.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PollutionLogger {

    private static final String APP_NAME_PROPERTY = "app.name";

    private PollutionLogger() {
    }

    /**
     * Names the service for the logging configuration. Logback reads the
     * {@code app.name} system property once, while configuring itself for the
     * first logger, so this must run before any logger is created — callers
     * place it in a static block above their logger field. A value supplied
     * from outside (a {@code -Dapp.name} JVM flag) wins over the code's own.
     */
    public static void initService(String serviceName) {
        if (System.getProperty(APP_NAME_PROPERTY) == null) {
            System.setProperty(APP_NAME_PROPERTY, serviceName);
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
}
