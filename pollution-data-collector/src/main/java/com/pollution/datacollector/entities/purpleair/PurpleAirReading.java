package com.pollution.datacollector.entities.purpleair;

import java.time.Instant;

/**
 * The fields the collector asks PurpleAir for, as returned for one sensor.
 *
 * @param pm25     PM2.5 concentration in µg/m³ (the raw {@code pm2.5} field)
 * @param lastSeen when the sensor last reported, i.e. the timestamp of the reading itself
 */
public record PurpleAirReading(double pm25, Instant lastSeen) {
}
