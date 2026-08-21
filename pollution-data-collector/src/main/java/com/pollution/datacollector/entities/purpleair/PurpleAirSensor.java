package com.pollution.datacollector.entities.purpleair;

/**
 * The PurpleAir sensors the collector follows, keyed by PurpleAir's
 * {@code sensor_index} (the {@code select=} value in a map.purpleair.com URL).
 * <p>
 * Everything else about a sensor (name, coordinates) is fetched from the API at
 * startup, see {@link PurpleAirSensorInfo}. The city is ours: PurpleAir does not
 * expose one, and it becomes {@code PollutionData.city()}.
 */
public enum PurpleAirSensor {

    GANEI_AYALON(308702, "Ganei Ayalon"),
    SHOHAM_HAETROG(298123, "Shoham");

    private final int sensorIndex;
    private final String city;

    PurpleAirSensor(int sensorIndex, String city) {
        this.sensorIndex = sensorIndex;
        this.city = city;
    }

    public int sensorIndex() {
        return sensorIndex;
    }

    public String city() {
        return city;
    }
}
