package com.pollution.datacollector.entities.purpleair;

import java.util.Objects;

/**
 * A PurpleAir sensor the collector follows: PurpleAir's {@code sensor_index}
 * (the {@code select=} value in a map.purpleair.com URL) and its city. The
 * city is ours — PurpleAir does not expose one — and it becomes
 * {@code PollutionData.city()}. Everything else about a sensor (name,
 * coordinates) is fetched from the API at startup, see {@link PurpleAirSensorInfo}.
 * <p>
 * Which sensors an instance follows is configuration, not code, so a sensor
 * has a textual form, {@code <sensorIndex>=<city>} ({@code 308702=Ganei Ayalon}):
 * the collector's {@code PURPLEAIR_SENSORS} setting lists sensors in it,
 * {@link #parse} reads it and {@link #toString()} writes it.
 *
 * @param sensorIndex PurpleAir's id of the sensor; positive
 * @param city        the city the sensor stands in; not blank
 */
public record PurpleAirSensor(int sensorIndex, String city) {

    private static final char SEPARATOR = '=';

    public PurpleAirSensor {
        if (sensorIndex <= 0) {
            throw new IllegalArgumentException("sensorIndex must be positive, was " + sensorIndex);
        }
        Objects.requireNonNull(city, "city");
        if (city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
    }

    /**
     * Reads a sensor from its textual form, {@code <sensorIndex>=<city>};
     * whitespace around either part is ignored.
     *
     * @throws IllegalArgumentException if the text is not of that form
     */
    public static PurpleAirSensor parse(String text) {
        Objects.requireNonNull(text, "text");
        int separator = text.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException(
                    "sensor '" + text + "' is not of the form <sensorIndex>" + SEPARATOR + "<city>");
        }
        String index = text.substring(0, separator).trim();
        String city = text.substring(separator + 1).trim();
        int sensorIndex;
        try {
            sensorIndex = Integer.parseInt(index);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("sensor '" + text + "': '" + index + "' is not a sensor index", e);
        }
        if (city.isEmpty()) {
            throw new IllegalArgumentException("sensor '" + text + "' names no city");
        }
        return new PurpleAirSensor(sensorIndex, city);
    }

    /** The textual form {@link #parse} reads, {@code <sensorIndex>=<city>}. */
    @Override
    public String toString() {
        return sensorIndex + String.valueOf(SEPARATOR) + city;
    }
}
