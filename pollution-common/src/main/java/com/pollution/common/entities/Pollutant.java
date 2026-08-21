package com.pollution.common.entities;

/**
 * Pollutants tracked by the system, with the unit their concentration is reported in.
 */
public enum Pollutant {
    PM2_5("PM2.5", "µg/m³"),
    PM10("PM10", "µg/m³"),
    NO2("NO2", "µg/m³"),
    O3("O3", "µg/m³"),
    SO2("SO2", "µg/m³"),
    CO("CO", "mg/m³");

    private final String displayName;
    private final String unit;

    Pollutant(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }

    public String displayName() {
        return displayName;
    }

    public String unit() {
        return unit;
    }
}
