package com.pollution.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PollutionLogger {

    private PollutionLogger() {
    }

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
}
