package com.pollution.common.config;

import java.util.Arrays;
import java.util.List;

/**
 * The one place that reads environment variables. Each service's {@code Config}
 * declares its own variable names and defaults and uses these helpers to read
 * them; nothing else in a service should touch {@link System#getenv}.
 */
public final class Env {

    private Env() {
    }

    public static String getString(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public static int getInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
    }

    public static long getLong(String name, long defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value.trim());
    }

    /** Reads a comma-separated list; blank entries are dropped. */
    public static List<String> getList(String name, List<String> defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
