package com.pollution.datacollector.fetchers.purpleair;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.api.purpleair.PurpleAirSensorApi;
import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.registry.ISensorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Reads every sensor in the {@link ISensorRegistry} through a {@link PurpleAirSensorApi}
 * and maps the readings to {@link PollutionData}.
 * <p>
 * {@link #initialize()} enriches the registry with each sensor's PurpleAir metadata,
 * which supplies the reading's {@code source} name. A sensor that could not be
 * enriched still produces readings (named after its enum constant) and is
 * retried on the next cycle.
 * <p>
 * Every {@code source} is prefixed with {@value #SOURCE_PREFIX} so readings from
 * different providers can never collide on name.
 */
public class PurpleAirReadingsFetcher implements IReadingsFetcher {

    private static final Logger logger = PollutionLogger.getLogger(PurpleAirReadingsFetcher.class);
    /** Namespaces PurpleAir sources in {@link PollutionData#source()}. */
    static final String SOURCE_PREFIX = "purpleair:";

    private final PurpleAirSensorApi sensorApi;
    private final ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry;

    public PurpleAirReadingsFetcher(PurpleAirSensorApi sensorApi,
                                    ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry) {
        this.sensorApi = sensorApi;
        this.sensorRegistry = sensorRegistry;
    }

    @Override
    public void initialize() {
        sensorRegistry.enrichAll();
    }

    @Override
    public List<PollutionData> fetch() {
        List<PurpleAirSensor> sensors = sensorRegistry.sensors();
        List<PollutionData> readings = new ArrayList<>(sensors.size());
        for (PurpleAirSensor sensor : sensors) {
            try {
                String source = sourceName(sensor);
                PurpleAirReading reading = sensorApi.fetchPm25(sensor.sensorIndex());
                readings.add(new PollutionData(
                        sensor.city(), source, Pollutant.PM2_5, reading.pm25(), reading.lastSeen()));
            } catch (PurpleAirApiException e) {
                logger.error("failed to read sensor {} (index {}): {}",
                        sensor, sensor.sensorIndex(), e.getMessage(), e);
            }
        }
        return readings;
    }

    /** {@value #SOURCE_PREFIX} + the sensor's PurpleAir name, enriching it now if startup enrichment failed. */
    private String sourceName(PurpleAirSensor sensor) {
        String name = sensorRegistry.get(sensor)
                .or(() -> retryEnrichment(sensor))
                .map(PurpleAirSensorInfo::name)
                .orElse(sensor.name());
        return SOURCE_PREFIX + name;
    }

    private Optional<PurpleAirSensorInfo> retryEnrichment(PurpleAirSensor sensor) {
        try {
            return Optional.of(sensorRegistry.enrichSensor(sensor));
        } catch (PurpleAirApiException e) {
            logger.warn("sensor {} still not enriched ({}); using its enum name as source",
                    sensor, e.getMessage());
            return Optional.empty();
        }
    }
}
