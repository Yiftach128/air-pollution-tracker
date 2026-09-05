package com.pollution.alertservice.senders.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Only the words: how an alert is rendered for people. The Bot API call
 * itself is Telegram's and is not tested here.
 */
class TelegramAlertSenderTest {

    private static final Instant AT = Instant.parse("2026-09-05T10:00:00Z");

    private final TelegramAlertSender sender = new TelegramAlertSender(
            "https://example.invalid", "123456:token", "-1001234567890", Duration.ofSeconds(1));

    private static PollutionAlert alert(Pollutant pollutant, Duration window, double value, double threshold) {
        return new PollutionAlert("Tel Aviv", "purpleair:Ganei-Ayalon", pollutant, window, value, threshold, AT);
    }

    @Test
    void anAlertIsFiveLinesNamingThePollutantSensorCauseValueAndThreshold() {
        String text = sender.toText(alert(Pollutant.PM2_5, Duration.ofMinutes(10), 31, 30));

        assertEquals("🚨 PM2.5 alert\n"
                + "Sensor: Tel Aviv\n"
                + "Cause: High 10-minute average\n"
                + "Value: 31.0 µg/m³\n"
                + "Threshold: 30.0 µg/m³", text);
    }

    @Test
    void aSingleReadingIsNamedAsTheCause() {
        String text = sender.toText(alert(Pollutant.PM2_5, null, 60, 50));

        assertEquals("Cause: High single reading", text.split("\n")[2]);
    }

    @Test
    void valuesAreRoundedToOneDecimalInThePollutantsUnit() {
        String text = sender.toText(alert(Pollutant.CO, Duration.ofHours(24), 4.567, 4));

        assertEquals("Value: 4.6 mg/m³", text.split("\n")[3]);
        assertEquals("Threshold: 4.0 mg/m³", text.split("\n")[4]);
    }

    @Test
    void aWindowIsDescribedInHoursOrMinutes() {
        assertEquals("single reading", TelegramAlertSender.describeMeasurement(null));
        assertEquals("10-minute average", TelegramAlertSender.describeMeasurement(Duration.ofMinutes(10)));
        assertEquals("1-hour average", TelegramAlertSender.describeMeasurement(Duration.ofHours(1)));
        assertEquals("24-hour average", TelegramAlertSender.describeMeasurement(Duration.ofHours(24)));
        assertEquals("PT1M30S average", TelegramAlertSender.describeMeasurement(Duration.ofSeconds(90)));
    }

    @Test
    void theChannelSettingsMustBeComplete() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelegramAlertSender(" ", "token", "-100", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TelegramAlertSender("https://example.invalid", " ", "-100", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TelegramAlertSender("https://example.invalid", "token", "", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new TelegramAlertSender("https://example.invalid", "token", "-100", Duration.ZERO));
    }
}
