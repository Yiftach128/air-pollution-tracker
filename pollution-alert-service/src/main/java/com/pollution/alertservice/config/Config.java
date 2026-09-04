package com.pollution.alertservice.config;

import static com.pollution.common.config.Config.getThresholdsFile;

import com.pollution.common.config.Env;
import com.pollution.common.thresholds.Thresholds;
import com.pollution.common.thresholds.ThresholdsLoader;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The alert service's settings. Values only; see {@link Wiring} for how the
 * service is assembled from them.
 */
public final class Config {

    public static final String SERVICE_NAME = "pollution-alert-service";

    /** Cache key prefix for the last alert sent for a series; the source, pollutant and window are appended. */
    public static final String LAST_SENT_KEY_PREFIX = "alert:last-sent:";

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

    private Config() {
    }

    /**
     * The thresholds shared with every other service that judges a value
     * against one, from {@code thresholds.json} in pollution-common — or the
     * file {@code THRESHOLDS_FILE} names — so an alert and the dashboard's
     * marking of the same value cannot disagree.
     */
    public static Thresholds getThresholds() {
        return getThresholdsFile().map(ThresholdsLoader::load).orElseGet(ThresholdsLoader::load);
    }

    /** The cooldown of each given rolling-average window (those the thresholds have a factor for), keyed by window. */
    public static Map<Duration, Duration> getWindowCooldowns(Set<Duration> windows) {
        Map<Duration, Duration> cooldowns = new HashMap<>();
        for (Duration window : windows) {
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
