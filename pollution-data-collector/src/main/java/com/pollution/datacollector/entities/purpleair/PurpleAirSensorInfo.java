package com.pollution.datacollector.entities.purpleair;

import java.util.Objects;

/**
 * A sensor's metadata as reported by PurpleAir, fetched once at startup.
 *
 * @param sensorIndex PurpleAir's id of the sensor
 * @param name        the name its owner gave it, e.g. {@code "Ganei-Ayalon"}
 * @param latitude    location, decimal degrees
 * @param longitude   location, decimal degrees
 */
public record PurpleAirSensorInfo(int sensorIndex, String name, double latitude, double longitude) {

    public PurpleAirSensorInfo {
        Objects.requireNonNull(name, "name");
    }
}
