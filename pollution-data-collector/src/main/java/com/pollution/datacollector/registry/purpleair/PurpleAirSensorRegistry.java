package com.pollution.datacollector.registry.purpleair;

import com.pollution.common.PollutionLogger;
import com.pollution.datacollector.api.purpleair.IPurpleAirSensorApi;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.registry.ISensorRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * In-memory {@link ISensorRegistry} backed by a {@link IPurpleAirSensorApi}.
 * It is given every configured sensor — the same list on every instance —
 * and follows its {@link Shard}'s share of them, logging each change of
 * share, so an instance's log says which sensors it owns at any time.
 * Metadata once fetched is kept even for a sensor no longer followed, so
 * regaining it costs no request.
 */
public class PurpleAirSensorRegistry implements ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> {

    private static final Logger logger = PollutionLogger.getLogger(PurpleAirSensorRegistry.class);

    private final IPurpleAirSensorApi sensorApi;
    private final List<PurpleAirSensor> configured;
    private final Map<PurpleAirSensor, PurpleAirSensorInfo> infoBySensor = new ConcurrentHashMap<>();
    private volatile List<PurpleAirSensor> following = List.of();

    /**
     * @param configured every sensor the group polls between them; at least one, no sensor index twice
     */
    public PurpleAirSensorRegistry(IPurpleAirSensorApi sensorApi, Collection<PurpleAirSensor> configured) {
        this.sensorApi = Objects.requireNonNull(sensorApi, "sensorApi");
        this.configured = List.copyOf(Objects.requireNonNull(configured, "configured"));
        if (this.configured.isEmpty()) {
            throw new IllegalArgumentException("at least one sensor is required");
        }
        Set<Integer> indexes = new HashSet<>();
        for (PurpleAirSensor sensor : this.configured) {
            if (!indexes.add(sensor.sensorIndex())) {
                throw new IllegalArgumentException("sensor index " + sensor.sensorIndex() + " is listed more than once");
            }
        }
        logger.info("{} sensor(s) configured: {}; following none until given a shard", this.configured.size(), this.configured);
    }

    @Override
    public List<PurpleAirSensor> sensors() {
        return following;
    }

    @Override
    public void follow(Shard shard) {
        List<PurpleAirSensor> share = shard.select(configured);
        List<PurpleAirSensor> gained = new ArrayList<>(share);
        gained.removeAll(following);
        List<PurpleAirSensor> dropped = new ArrayList<>(following);
        dropped.removeAll(share);
        following = share;
        logger.info("shard {}: following {} of {} sensors: {} (gained {}, dropped {})",
                shard, share.size(), configured.size(), share, gained, dropped);
        for (PurpleAirSensor sensor : gained) {
            if (infoBySensor.containsKey(sensor)) {
                continue;
            }
            try {
                enrichSensor(sensor);
            } catch (PurpleAirApiException e) {
                logger.error("failed to enrich sensor {} (index {}): {}",
                        sensor, sensor.sensorIndex(), e.getMessage(), e);
            }
        }
    }

    @Override
    public PurpleAirSensorInfo enrichSensor(PurpleAirSensor sensor) {
        PurpleAirSensorInfo info = sensorApi.fetchSensorInfo(sensor.sensorIndex());
        infoBySensor.put(sensor, info);
        logger.info("enriched sensor {}: {}", sensor, info);
        return info;
    }

    @Override
    public Optional<PurpleAirSensorInfo> get(PurpleAirSensor sensor) {
        return Optional.ofNullable(infoBySensor.get(sensor));
    }
}
