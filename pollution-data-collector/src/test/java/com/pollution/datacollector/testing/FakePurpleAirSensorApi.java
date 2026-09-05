package com.pollution.datacollector.testing;

import com.pollution.datacollector.api.purpleair.IPurpleAirSensorApi;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IPurpleAirSensorApi} that answers from what the test told it,
 * sensor by sensor: a name, a reading, or a failure. Remembers which
 * sensors were asked for what, so a test can assert what was fetched.
 */
public final class FakePurpleAirSensorApi implements IPurpleAirSensorApi {

    private final Map<Integer, PurpleAirSensorInfo> infos = new HashMap<>();
    private final Map<Integer, PurpleAirReading> readings = new HashMap<>();
    private final Map<Integer, PurpleAirApiException> infoFailures = new HashMap<>();
    private final Map<Integer, PurpleAirApiException> readingFailures = new HashMap<>();
    private final List<Integer> infoRequests = new ArrayList<>();
    private final List<Integer> readingRequests = new ArrayList<>();

    /** The sensor's metadata: PurpleAir knows it by this name. */
    public void knows(int sensorIndex, String name) {
        infos.put(sensorIndex, new PurpleAirSensorInfo(sensorIndex, name, 31.9, 34.9));
        infoFailures.remove(sensorIndex);
    }

    /** The sensor's current reading. */
    public void reads(int sensorIndex, double pm25, Instant lastSeen) {
        readings.put(sensorIndex, new PurpleAirReading(pm25, lastSeen));
        readingFailures.remove(sensorIndex);
    }

    /** Asking for the sensor's metadata fails with this, until {@link #knows} is called for it. */
    public void failsInfo(int sensorIndex, PurpleAirApiException failure) {
        infoFailures.put(sensorIndex, failure);
    }

    /** Asking for the sensor's reading fails with this, until {@link #reads} is called for it. */
    public void failsReading(int sensorIndex, PurpleAirApiException failure) {
        readingFailures.put(sensorIndex, failure);
    }

    /** The sensors whose metadata was asked for, in order, repeats included. */
    public List<Integer> infoRequests() {
        return List.copyOf(infoRequests);
    }

    /** The sensors whose reading was asked for, in order, repeats included. */
    public List<Integer> readingRequests() {
        return List.copyOf(readingRequests);
    }

    @Override
    public PurpleAirReading fetchPm25(int sensorIndex) {
        readingRequests.add(sensorIndex);
        PurpleAirApiException failure = readingFailures.get(sensorIndex);
        if (failure != null) {
            throw failure;
        }
        PurpleAirReading reading = readings.get(sensorIndex);
        if (reading == null) {
            throw new PurpleAirApiException("no reading for sensor " + sensorIndex, 404);
        }
        return reading;
    }

    @Override
    public PurpleAirSensorInfo fetchSensorInfo(int sensorIndex) {
        infoRequests.add(sensorIndex);
        PurpleAirApiException failure = infoFailures.get(sensorIndex);
        if (failure != null) {
            throw failure;
        }
        PurpleAirSensorInfo info = infos.get(sensorIndex);
        if (info == null) {
            throw new PurpleAirApiException("no such sensor " + sensorIndex, 404);
        }
        return info;
    }
}
