package com.pollution.datacollector.fetchers.purpleair;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.SourceId;
import com.pollution.datacollector.api.purpleair.IPurpleAirSensorApi;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.registry.ISensorRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Reads every sensor the {@link ISensorRegistry} follows through a
 * {@link IPurpleAirSensorApi} and maps the readings to {@link PollutionData}.
 * <p>
 * {@link #follow(Shard)} hands the shard to the registry, which enriches the
 * sensors gained with their PurpleAir metadata — that supplies the reading's
 * {@code source} name. A sensor that could not be enriched still produces
 * readings (named after its sensor index) and is retried on every fetch.
 * <p>
 * Every {@code source} is a {@link SourceId} of provider {@value #PROVIDER}
 * ({@code purpleair:<name>}), so readings from different providers can never
 * collide on name.
 */
public class PurpleAirReadingsFetcher implements IReadingsFetcher {

    private static final Logger logger = PollutionLogger.getLogger(PurpleAirReadingsFetcher.class);
    /** The provider of every source this fetcher produces, see {@link PollutionData#source()}. */
    static final String PROVIDER = "purpleair";

    private final IPurpleAirSensorApi sensorApi;
    private final ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry;

    public PurpleAirReadingsFetcher(IPurpleAirSensorApi sensorApi,
                                    ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> sensorRegistry) {
        this.sensorApi = sensorApi;
        this.sensorRegistry = sensorRegistry;
    }

    @Override
    public void follow(Shard shard) {
        sensorRegistry.follow(shard);
    }

    @Override
    public Set<String> sources() {
        Set<String> sources = new LinkedHashSet<>();
        for (PurpleAirSensor sensor : sensorRegistry.sensors()) {
            sources.add(sourceOf(sensor, sensorRegistry.get(sensor)));
        }
        return sources;
    }

    @Override
    public List<PollutionData> fetch() {
        List<PurpleAirSensor> sensors = sensorRegistry.sensors();
        List<PollutionData> readings = new ArrayList<>(sensors.size());
        for (PurpleAirSensor sensor : sensors) {
            try {
                String source = sourceOf(sensor, sensorRegistry.get(sensor).or(() -> retryEnrichment(sensor)));
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

    /** The {@link SourceId} of the sensor's PurpleAir name, or of its sensor index while it has none. */
    private static String sourceOf(PurpleAirSensor sensor, Optional<PurpleAirSensorInfo> info) {
        String name = info.map(PurpleAirSensorInfo::name).orElse(String.valueOf(sensor.sensorIndex()));
        return new SourceId(PROVIDER, name).toString();
    }

    private Optional<PurpleAirSensorInfo> retryEnrichment(PurpleAirSensor sensor) {
        try {
            return Optional.of(sensorRegistry.enrichSensor(sensor));
        } catch (PurpleAirApiException e) {
            logger.warn("sensor {} still not enriched ({}); using its sensor index as source",
                    sensor, e.getMessage());
            return Optional.empty();
        }
    }
}
