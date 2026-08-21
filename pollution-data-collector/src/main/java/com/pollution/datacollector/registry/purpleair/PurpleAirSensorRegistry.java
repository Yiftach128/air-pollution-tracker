package com.pollution.datacollector.registry.purpleair;

import com.pollution.common.PollutionLogger;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.api.purpleair.PurpleAirSensorApi;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.registry.ISensorRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * In-memory {@link ISensorRegistry} backed by a {@link PurpleAirSensorApi}.
 */
public class PurpleAirSensorRegistry implements ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> {

    private static final Logger logger = PollutionLogger.getLogger(PurpleAirSensorRegistry.class);

    private final PurpleAirSensorApi sensorApi;
    private final List<PurpleAirSensor> sensors;
    private final Map<PurpleAirSensor, PurpleAirSensorInfo> infoBySensor = new ConcurrentHashMap<>();

    public PurpleAirSensorRegistry(PurpleAirSensorApi sensorApi, Collection<PurpleAirSensor> sensors) {
        this.sensorApi = sensorApi;
        this.sensors = List.copyOf(sensors);
    }

    @Override
    public List<PurpleAirSensor> sensors() {
        return sensors;
    }

    @Override
    public PurpleAirSensorInfo enrichSensor(PurpleAirSensor sensor) {
        PurpleAirSensorInfo info = sensorApi.fetchSensorInfo(sensor.sensorIndex());
        infoBySensor.put(sensor, info);
        logger.info("enriched sensor {}: {}", sensor, info);
        return info;
    }

    @Override
    public void enrichAll() {
        for (PurpleAirSensor sensor : sensors) {
            try {
                enrichSensor(sensor);
            } catch (PurpleAirApiException e) {
                logger.error("failed to enrich sensor {} (index {}): {}",
                        sensor, sensor.sensorIndex(), e.getMessage(), e);
            }
        }
        logger.info("enriched {}/{} sensors", infoBySensor.size(), sensors.size());
    }

    @Override
    public Optional<PurpleAirSensorInfo> get(PurpleAirSensor sensor) {
        return Optional.ofNullable(infoBySensor.get(sensor));
    }
}
