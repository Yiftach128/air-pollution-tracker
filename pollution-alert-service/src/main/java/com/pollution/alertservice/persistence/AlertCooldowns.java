package com.pollution.alertservice.persistence;

import com.pollution.alertservice.entities.AlertSeries;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How long each kind of {@link AlertSeries} stays quiet after an alert. A
 * window's average is the same news for about one window, so a window with
 * no explicit cooldown uses its own length; a single reading — a spike — is
 * a moment, so it has a cooldown of its own. Immutable.
 */
public final class AlertCooldowns {

    private final Map<Duration, Duration> windowCooldowns;
    private final Duration readingCooldown;

    /**
     * @param windowCooldowns the cooldown of each rolling-average window that
     *                        has an explicit one; each positive
     * @param readingCooldown the cooldown of a single reading; positive
     */
    public AlertCooldowns(Map<Duration, Duration> windowCooldowns, Duration readingCooldown) {
        Objects.requireNonNull(windowCooldowns, "windowCooldowns");
        Map<Duration, Duration> copy = new HashMap<>();
        for (Map.Entry<Duration, Duration> entry : windowCooldowns.entrySet()) {
            Duration window = Objects.requireNonNull(entry.getKey(), "window");
            requirePositive("cooldown for window " + window, entry.getValue());
            copy.put(window, entry.getValue());
        }
        requirePositive("cooldown for single readings", readingCooldown);
        this.windowCooldowns = Collections.unmodifiableMap(copy);
        this.readingCooldown = readingCooldown;
    }

    private static void requirePositive(String what, Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException("no " + what);
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(what + " must be positive, was " + duration);
        }
    }

    /** How long this series stays quiet after an alert. */
    public Duration cooldownOf(AlertSeries series) {
        Duration window = series.window();
        if (window == null) {
            return readingCooldown;
        }
        return windowCooldowns.getOrDefault(window, window);
    }
}
