package com.pollution.common.message;

import java.time.Instant;
import java.util.Objects;

/**
 * Raised when a pollutant's concentration at a source exceeds its threshold.
 *
 * @param city          the city the source is located in
 * @param source        identifier of the sensor/station where the threshold was exceeded
 * @param pollutant     the pollutant that exceeded its threshold
 * @param measuredValue the concentration that triggered the alert, in {@link Pollutant#unit()}
 * @param threshold     the threshold that was exceeded, in {@link Pollutant#unit()}
 * @param timestamp     when the alert was raised
 */
public record PollutionAlert(String city,
                             String source,
                             Pollutant pollutant,
                             double measuredValue,
                             double threshold,
                             Instant timestamp)
        implements IMessage {

    public PollutionAlert {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public String toString() {
        return "PollutionAlert{city=" + city + ", source=" + source
                + ", " + pollutant.displayName() + "=" + measuredValue
                + " exceeds " + threshold + " " + pollutant.unit()
                + ", at=" + timestamp + "}";
    }
}
