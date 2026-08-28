package com.pollution.alertservice.persistence;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.persistence.IPollutionCache;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the last alert raised for each series in an {@link IPollutionCache}
 * under the key {@code <keyPrefix><source>:<POLLUTANT>:<window>}, where
 * {@code <window>} is the ISO-8601 window ({@code PT1H}) or {@code raw} for
 * single readings — e.g. {@code alert:last-sent:12345:PM2_5:PT24H}. Every
 * entry is stored with a lifetime of its series' cooldown, so the cache
 * itself ends the cooldown by dropping the key: a series is cooling down
 * exactly while its key exists. A source's series are found with the pattern
 * {@code <keyPrefix><source>:<POLLUTANT>:*}. Works with any cache
 * implementation; the concrete one is chosen in Wiring.
 */
public final class CacheBackedAlertCooldownStore implements IAlertCooldownStore {

    private static final String READING_WINDOW_TOKEN = "raw";
    private static final String GLOB_SPECIALS = "*?[]\\";

    private final IPollutionCache<PollutionAlert> cache;
    private final String keyPrefix;
    private final AlertCooldowns cooldowns;

    /**
     * @param cache     where the last alerts live
     * @param keyPrefix the alert service's key namespace; must not be blank
     * @param cooldowns how long each kind of series stays suppressed after an alert
     */
    public CacheBackedAlertCooldownStore(IPollutionCache<PollutionAlert> cache, String keyPrefix, AlertCooldowns cooldowns) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
    }

    @Override
    public Set<AlertSeries> coolingDownSeries(String source, Pollutant pollutant) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        Set<AlertSeries> series = new HashSet<>();
        for (PollutionAlert lastSent : cache.getObjectValuesByPattern(patternOf(source, pollutant))) {
            AlertSeries candidate = AlertSeries.of(lastSent);
            // the pattern can also match another source whose name ends with ours; the value is the truth
            if (candidate.source().equals(source) && candidate.pollutant() == pollutant) {
                series.add(candidate);
            }
        }
        return series;
    }

    @Override
    public void markSent(PollutionAlert alert) {
        AlertSeries series = AlertSeries.of(alert);
        cache.setObjectValue(keyOf(series), alert, cooldowns.cooldownOf(series));
    }

    @Override
    public void close() {
        cache.close();
    }

    private String keyOf(AlertSeries series) {
        String windowToken = series.window() == null ? READING_WINDOW_TOKEN : series.window().toString();
        return keyPrefix + series.source() + ":" + series.pollutant().name() + ":" + windowToken;
    }

    private String patternOf(String source, Pollutant pollutant) {
        return escapeGlob(keyPrefix + source + ":" + pollutant.name() + ":") + "*";
    }

    /** Escapes the glob characters of a literal so it matches only itself in a pattern. */
    private static String escapeGlob(String literal) {
        StringBuilder escaped = new StringBuilder(literal.length());
        for (char c : literal.toCharArray()) {
            if (GLOB_SPECIALS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
