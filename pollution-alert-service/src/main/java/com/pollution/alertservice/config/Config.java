package com.pollution.alertservice.config;

import com.pollution.common.config.Env;
import com.pollution.common.entities.Pollutant;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The alert service's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-alert-service";

    /** Cache key prefix for the last alert sent for a series; the source, pollutant and window are appended. */
    public static final String LAST_SENT_KEY_PREFIX = "alert:last-sent:";

    /**
     * Concentrations above which a measurement is an alert, in each
     * pollutant's unit: the WHO 2021 air quality guideline levels (24-hour
     * means; 8-hour for O3 and CO), except PM2.5, set at 25 — WHO's 2021
     * interim target 4 (its 2005 guideline) — since the 2021 level of 15 is
     * exceeded too routinely to be worth a post. Each is overridden by the
     * env var {@code ALERT_THRESHOLD_<POLLUTANT>} (e.g. {@code ALERT_THRESHOLD_PM2_5}) when set.
     */
    private static final Map<Pollutant, Double> DEFAULT_THRESHOLDS = Map.of(
            Pollutant.PM2_5, 25.0,
            Pollutant.PM10, 45.0,
            Pollutant.NO2, 25.0,
            Pollutant.O3, 100.0,
            Pollutant.SO2, 40.0,
            Pollutant.CO, 4.0
    );
    private static final String THRESHOLD_ENV_PREFIX = "ALERT_THRESHOLD_";

    /**
     * How much higher than its pollutant's threshold a measurement must be to
     * alert, by measurement type. The thresholds above are 24-hour guideline
     * levels, so a 24-hour average uses them as they are (×1); shorter windows
     * swing more, so they need more; a single reading — a spike — the most.
     * Each is overridden by {@code ALERT_THRESHOLD_FACTOR_<WINDOW>} with the
     * window in ISO-8601 ({@code ALERT_THRESHOLD_FACTOR_PT10M}, {@code _PT1H},
     * {@code _PT24H}) and {@code ALERT_THRESHOLD_FACTOR_RAW} for single
     * readings. A window with no factor uses ×1.
     */
    private static final Map<Duration, Double> DEFAULT_WINDOW_THRESHOLD_FACTORS = Map.of(
            Duration.ofMinutes(10), 2.0,
            Duration.ofHours(1), 1.5,
            Duration.ofHours(24), 1.0
    );
    private static final double DEFAULT_READING_THRESHOLD_FACTOR = 2.5;
    private static final String THRESHOLD_FACTOR_ENV_PREFIX = "ALERT_THRESHOLD_FACTOR_";
    private static final String READING_THRESHOLD_FACTOR_ENV = THRESHOLD_FACTOR_ENV_PREFIX + "RAW";

    /**
     * How long a series stays quiet after an alert — the inputs repeat every
     * few seconds, the alerts must not — by measurement type. A window's
     * average is the same news for about one window, so its cooldown is the
     * window itself; a single reading is a moment, so a spike gets
     * {@value #DEFAULT_READING_COOLDOWN_MINUTES} minutes. Each is overridden by
     * {@code ALERT_COOLDOWN_MINUTES_<WINDOW>} ({@code _PT10M}, {@code _PT1H},
     * {@code _PT24H}) and {@code ALERT_COOLDOWN_MINUTES_RAW} for single
     * readings; {@code ALERT_COOLDOWN_MINUTES} alone overrides every type at
     * once (handy for testing), the per-type variables winning over it.
     */
    private static final long DEFAULT_READING_COOLDOWN_MINUTES = 10;
    private static final String COOLDOWN_ENV = "ALERT_COOLDOWN_MINUTES";
    private static final String COOLDOWN_ENV_PREFIX = COOLDOWN_ENV + "_";
    private static final String READING_COOLDOWN_ENV = COOLDOWN_ENV_PREFIX + "RAW";

    /**
     * Telegram delivery: alerts are posted to one dedicated channel through
     * the Bot API (the bot must be an admin of the channel with "post
     * messages"). It is on when {@code TELEGRAM_BOT_TOKEN} and
     * {@code TELEGRAM_CHAT_ID} are both set (see {@link #isTelegramConfigured()}).
     */
    private static final String DEFAULT_TELEGRAM_API_BASE_URL = "https://api.telegram.org";
    private static final Duration DEFAULT_TELEGRAM_TIMEOUT = Duration.ofSeconds(10);

    /** The zone alert times are shown in to people; overridden by {@code ALERT_TIME_ZONE} (an IANA name). */
    private static final String DEFAULT_ALERT_TIME_ZONE = "Asia/Jerusalem";

    private Config() {
    }

    /** The threshold of every {@link Pollutant}. */
    public static Map<Pollutant, Double> getThresholds() {
        Map<Pollutant, Double> thresholds = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            Double defaultThreshold = DEFAULT_THRESHOLDS.get(pollutant);
            if (defaultThreshold == null) {
                throw new IllegalStateException("no default alert threshold for " + pollutant);
            }
            thresholds.put(pollutant, Env.getDouble(THRESHOLD_ENV_PREFIX + pollutant.name(), defaultThreshold));
        }
        return Collections.unmodifiableMap(thresholds);
    }

    /** The threshold factor of every rolling-average window that has one, keyed by window. */
    public static Map<Duration, Double> getWindowThresholdFactors() {
        Map<Duration, Double> factors = new HashMap<>();
        for (Map.Entry<Duration, Double> entry : DEFAULT_WINDOW_THRESHOLD_FACTORS.entrySet()) {
            Duration window = entry.getKey();
            factors.put(window, Env.getDouble(THRESHOLD_FACTOR_ENV_PREFIX + window, entry.getValue()));
        }
        return Collections.unmodifiableMap(factors);
    }

    /** The threshold factor of a single reading. */
    public static double getReadingThresholdFactor() {
        return Env.getDouble(READING_THRESHOLD_FACTOR_ENV, DEFAULT_READING_THRESHOLD_FACTOR);
    }

    /** The cooldown of every rolling-average window the service knows (those with a threshold factor), keyed by window. */
    public static Map<Duration, Duration> getWindowCooldowns() {
        Map<Duration, Duration> cooldowns = new HashMap<>();
        for (Duration window : DEFAULT_WINDOW_THRESHOLD_FACTORS.keySet()) {
            long defaultMinutes = Env.getLong(COOLDOWN_ENV, window.toMinutes());
            cooldowns.put(window, Duration.ofMinutes(Env.getLong(COOLDOWN_ENV_PREFIX + window, defaultMinutes)));
        }
        return Collections.unmodifiableMap(cooldowns);
    }

    /** The cooldown of a single reading. */
    public static Duration getReadingCooldown() {
        long defaultMinutes = Env.getLong(COOLDOWN_ENV, DEFAULT_READING_COOLDOWN_MINUTES);
        return Duration.ofMinutes(Env.getLong(READING_COOLDOWN_ENV, defaultMinutes));
    }

    /** The bot's token from BotFather; blank when Telegram delivery is off. */
    public static String getTelegramBotToken() {
        return Env.getString("TELEGRAM_BOT_TOKEN", "");
    }

    /**
     * The channel alerts are posted to: its numeric id ({@code -100…}) or,
     * for a public channel, {@code @username}; blank when Telegram delivery is off.
     */
    public static String getTelegramChatId() {
        return Env.getString("TELEGRAM_CHAT_ID", "");
    }

    /** The Bot API's base URL; overridable to point at a proxy or a test double. */
    public static String getTelegramApiBaseUrl() {
        return Env.getString("TELEGRAM_API_BASE_URL", DEFAULT_TELEGRAM_API_BASE_URL);
    }

    /** The most one Bot API request may take. */
    public static Duration getTelegramTimeout() {
        return Duration.ofSeconds(Env.getLong("TELEGRAM_TIMEOUT_SECONDS", DEFAULT_TELEGRAM_TIMEOUT.toSeconds()));
    }

    /** The time zone alert times are shown in. */
    public static ZoneId getAlertTimeZone() {
        return ZoneId.of(Env.getString("ALERT_TIME_ZONE", DEFAULT_ALERT_TIME_ZONE));
    }

    /**
     * True when both the bot token and the chat id are set, false when
     * neither is.
     *
     * @throws IllegalStateException when only one of them is set: a
     *                               half-configured channel is a mistake,
     *                               not a request for log-only delivery
     */
    public static boolean isTelegramConfigured() {
        boolean hasToken = !getTelegramBotToken().isBlank();
        boolean hasChat = !getTelegramChatId().isBlank();
        if (hasToken != hasChat) {
            throw new IllegalStateException(
                    "TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID must be set together; only "
                            + (hasToken ? "TELEGRAM_BOT_TOKEN" : "TELEGRAM_CHAT_ID") + " is set");
        }
        return hasToken;
    }
}
