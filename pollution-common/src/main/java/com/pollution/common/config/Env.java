package com.pollution.common.config;

import com.pollution.common.PollutionLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * The one place that reads environment variables. Each service's {@code Config}
 * declares its own variable names and defaults and uses these helpers to read
 * them; nothing else in a service should touch {@link System#getenv}.
 * <p>
 * A variable comes from the process environment or, when it is not set there,
 * from the nearest {@code .env} file: the working directory's, else the first
 * found walking up its parents. That file holds {@code NAME=value} lines
 * (blank lines and {@code #} comment lines are ignored, an {@code export }
 * prefix and surrounding quotes are stripped, an empty value counts as unset)
 * and is for local settings and secrets such as the Telegram bot token. It is
 * git-ignored; {@code .env.example} in the repository root lists what goes in it.
 */
public final class Env {

    private static final Logger logger = PollutionLogger.getLogger(Env.class);

    private static final String DOTENV_FILE_NAME = ".env";
    private static final String EXPORT_PREFIX = "export ";
    private static final char BOM = '﻿';

    private static final Map<String, String> DOTENV = loadDotenv();

    private Env() {
    }

    public static String getString(String name, String defaultValue) {
        String value = lookup(name);
        return value == null ? defaultValue : value;
    }

    public static int getInt(String name, int defaultValue) {
        String value = lookup(name);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public static long getLong(String name, long defaultValue) {
        String value = lookup(name);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    public static double getDouble(String name, double defaultValue) {
        String value = lookup(name);
        return value == null ? defaultValue : Double.parseDouble(value);
    }

    /** Reads a comma-separated list; blank entries are dropped. */
    public static List<String> getList(String name, List<String> defaultValue) {
        String value = lookup(name);
        if (value == null) {
            return defaultValue;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    /**
     * The variable's value, trimmed: from the process environment, else from
     * the {@code .env} file; {@code null} when blank or unset in both.
     */
    private static String lookup(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = DOTENV.get(name);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> loadDotenv() {
        Path file = findDotenv();
        if (file == null) {
            return Map.of();
        }
        try {
            Map<String, String> values = parseDotenv(Files.readAllLines(file, StandardCharsets.UTF_8));
            logger.info("loaded {} setting(s) from {}", values.size(), file);
            return Collections.unmodifiableMap(values);
        } catch (IOException e) {
            logger.warn("could not read {}; using the process environment only", file, e);
            return Map.of();
        }
    }

    /** The nearest {@code .env}: in the working directory, else the closest parent that has one. */
    private static Path findDotenv() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(DOTENV_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Map<String, String> parseDotenv(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        boolean firstLine = true;
        for (String rawLine : lines) {
            String line = rawLine;
            if (firstLine) {
                firstLine = false;
                if (!line.isEmpty() && line.charAt(0) == BOM) {
                    line = line.substring(1);
                }
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(EXPORT_PREFIX)) {
                line = line.substring(EXPORT_PREFIX.length()).trim();
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
