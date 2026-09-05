package com.pollution.datacollector.api.purpleair;

import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;

/**
 * PurpleAir's per-sensor endpoint as the registry and the fetcher see it:
 * one sensor's current reading, one sensor's metadata. {@link PurpleAirSensorApi}
 * is the HTTP implementation; tests hand the registry and fetcher a fake.
 */
public interface IPurpleAirSensorApi {

    /**
     * Fetches the current PM2.5 reading of one sensor.
     *
     * @throws PurpleAirApiException if the request failed or the response lacked the reading
     */
    PurpleAirReading fetchPm25(int sensorIndex);

    /**
     * Fetches one sensor's metadata (name and location).
     *
     * @throws PurpleAirApiException if the request failed or the response lacked the metadata
     */
    PurpleAirSensorInfo fetchSensorInfo(int sensorIndex);
}
