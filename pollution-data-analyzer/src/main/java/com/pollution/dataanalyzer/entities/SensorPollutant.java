package com.pollution.dataanalyzer.entities;

import com.pollution.common.entities.Pollutant;
import java.util.Objects;

/**
 * Identity of one series of readings: a pollutant measured by one sensor.
 * Every rolling average, persisted state and published average belongs to
 * exactly one series.
 *
 * @param sensorId  identifier of the sensor, exactly as the collector published it
 *                  ({@code PollutionData.source()})
 * @param pollutant the pollutant the series measures
 */
public record SensorPollutant(String sensorId, Pollutant pollutant) {

    public SensorPollutant {
        Objects.requireNonNull(sensorId, "sensorId");
        Objects.requireNonNull(pollutant, "pollutant");
    }

    @Override
    public String toString() {
        return sensorId + "/" + pollutant.displayName();
    }
}
