package com.pollution.alertservice.senders.telegram;

import com.pollution.alertservice.senders.AlertSendException;
import com.pollution.alertservice.senders.IAlertSender;
import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Posts alerts to one Telegram channel — the alerts feed people subscribe to —
 * through the Bot API's {@code sendMessage}: one HTTP POST per alert, as plain
 * text with no markup, so nothing has to be escaped. The bot must be an admin
 * of the channel with permission to post.
 * <p>
 * The {@link PollutionAlert} is turned into words only here, at the edge; the
 * rest of the service only ever handles the entity. The message is five lines:
 * <pre>
 * 🚨 PM2.5 alert
 * Sensor: Tel Aviv
 * Cause: High 10-minute average
 * Value: 31.0 µg/m³
 * Threshold: 30.0 µg/m³
 * </pre>
 * The cause names the measurement ({@code High single reading} for a spike,
 * else the window's average); the value and the threshold each carry the
 * pollutant's unit. The source is not named: the sensor line's city places
 * the alert.
 * <p>
 * The bot token is part of the request URL, so the URL is never logged and
 * never put into an exception message.
 */
public final class TelegramAlertSender implements IAlertSender {

    private static final Logger logger = PollutionLogger.getLogger(TelegramAlertSender.class);

    private static final String SEND_MESSAGE_METHOD = "sendMessage";
    private static final int MAX_QUOTED_BODY_LENGTH = 300;

    private final HttpClient httpClient;
    private final URI sendMessageUri;
    private final String chatId;
    private final Duration timeout;

    /**
     * @param apiBaseUrl the Bot API's base URL, e.g. {@code https://api.telegram.org}
     * @param botToken   the bot's token from BotFather; must not be blank
     * @param chatId     the channel alerts are posted to: its numeric id ({@code -100…})
     *                   or {@code @username} for a public channel; must not be blank
     * @param timeout    the most one request may take to connect and complete; must be positive
     */
    public TelegramAlertSender(String apiBaseUrl, String botToken, String chatId, Duration timeout) {
        Objects.requireNonNull(apiBaseUrl, "apiBaseUrl");
        Objects.requireNonNull(botToken, "botToken");
        this.chatId = Objects.requireNonNull(chatId, "chatId");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("apiBaseUrl must not be blank");
        }
        if (botToken.isBlank()) {
            throw new IllegalArgumentException("botToken must not be blank");
        }
        if (chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, was " + timeout);
        }
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.sendMessageUri = URI.create(base + "/bot" + botToken + "/" + SEND_MESSAGE_METHOD);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        logger.info("posting alerts to Telegram channel {}", chatId);
    }

    @Override
    public void send(PollutionAlert alert) {
        Objects.requireNonNull(alert, "alert");
        String form = "chat_id=" + encode(chatId) + "&text=" + encode(toText(alert));
        HttpRequest request = HttpRequest.newBuilder(sendMessageUri)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AlertSendException("Telegram request to chat " + chatId + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AlertSendException("interrupted while sending to Telegram chat " + chatId, e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new AlertSendException("Telegram rejected the alert for chat " + chatId
                    + ": HTTP " + response.statusCode() + " " + abbreviate(response.body()));
        }
        logger.debug("delivered to Telegram chat {}: {}", chatId, alert);
    }

    /** The JDK HTTP client holds nothing that needs releasing on Java 17. */
    @Override
    public void close() {
    }

    /** The message people read; see the class comment for its shape. */
    String toText(PollutionAlert alert) {
        String unit = alert.pollutant().unit();
        return "🚨 " + alert.pollutant().displayName() + " alert\n"
                + "Sensor: " + alert.city() + "\n"
                + "Cause: High " + describeMeasurement(alert.window()) + "\n"
                + "Value: " + formatValue(alert.measuredValue()) + " " + unit + "\n"
                + "Threshold: " + formatValue(alert.threshold()) + " " + unit;
    }

    /** {@code "single reading"}, {@code "10-minute average"}, {@code "24-hour average"}. */
    static String describeMeasurement(Duration window) {
        if (window == null) {
            return "single reading";
        }
        long seconds = window.getSeconds();
        if (seconds % 3600 == 0) {
            return seconds / 3600 + "-hour average";
        }
        if (seconds % 60 == 0) {
            return seconds / 60 + "-minute average";
        }
        return window + " average";
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= MAX_QUOTED_BODY_LENGTH ? oneLine : oneLine.substring(0, MAX_QUOTED_BODY_LENGTH) + "…";
    }
}
