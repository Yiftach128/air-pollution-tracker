package com.pollution.datacollector.registry.purpleair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.pollution.datacollector.registry.ISensorRegistry;
import com.pollution.datacollector.testing.FakePurpleAirSensorApi;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PurpleAirSensorRegistryTest {

    private static final PurpleAirSensor GANEI_AYALON = new PurpleAirSensor(308702, "Ganei Ayalon");
    private static final PurpleAirSensor SHOHAM = new PurpleAirSensor(298123, "Shoham");
    private static final Shard ALL = new Shard(0, 1);

    private final FakePurpleAirSensorApi api = new FakePurpleAirSensorApi();
    private final ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> registry =
            new PurpleAirSensorRegistry(api, List.of(GANEI_AYALON, SHOHAM));

    @Test
    void followsNothingUntilGivenAShard() {
        assertEquals(List.of(), registry.sensors());
    }

    @Test
    void followsItsShardsShareOfTheConfiguredSensorsInOrder() {
        registry.follow(ALL);
        assertEquals(List.of(GANEI_AYALON, SHOHAM), registry.sensors());

        registry.follow(new Shard(1, 2));
        assertEquals(List.of(SHOHAM), registry.sensors());

        registry.follow(new Shard(0, 2));
        assertEquals(List.of(GANEI_AYALON), registry.sensors());

        registry.follow(Shard.NONE);
        assertEquals(List.of(), registry.sensors());
    }

    @Test
    void followingEnrichesTheSensorsGainedAndSkipsTheOnesThatFail() {
        api.failsInfo(GANEI_AYALON.sensorIndex(), new PurpleAirApiException("PurpleAir returned HTTP 500", 500));
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        registry.follow(ALL);

        assertEquals(Optional.empty(), registry.get(GANEI_AYALON));
        assertTrue(registry.get(SHOHAM).isPresent());
        assertEquals(List.of(GANEI_AYALON.sensorIndex(), SHOHAM.sensorIndex()), api.infoRequests());
        assertEquals(List.of(GANEI_AYALON, SHOHAM), registry.sensors(), "followed even un-enriched");
    }

    @Test
    void onlyTheSensorsGainedAreEnriched() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        registry.follow(new Shard(1, 2));
        assertEquals(List.of(SHOHAM.sensorIndex()), api.infoRequests());

        registry.follow(ALL);
        assertEquals(List.of(SHOHAM.sensorIndex(), GANEI_AYALON.sensorIndex()), api.infoRequests());
    }

    @Test
    void aSensorIsEnrichedOnceHoweverOftenItIsRegained() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        registry.follow(ALL);
        registry.follow(new Shard(1, 2));
        registry.follow(ALL);

        assertEquals(List.of(GANEI_AYALON.sensorIndex(), SHOHAM.sensorIndex()), api.infoRequests());
        assertTrue(registry.get(GANEI_AYALON).isPresent(), "kept while not followed");
    }

    @Test
    void knowsNothingAboutASensorUntilItIsEnriched() {
        assertEquals(Optional.empty(), registry.get(GANEI_AYALON));
    }

    @Test
    void enrichingASensorStoresAndReturnsItsMetadata() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");

        PurpleAirSensorInfo info = registry.enrichSensor(GANEI_AYALON);

        assertEquals("Ganei-Ayalon", info.name());
        assertEquals(Optional.of(info), registry.get(GANEI_AYALON));
    }

    @Test
    void aFailedEnrichmentLeavesTheRegistryUnchanged() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        registry.enrichSensor(GANEI_AYALON);
        api.failsInfo(GANEI_AYALON.sensorIndex(), new PurpleAirApiException("PurpleAir returned HTTP 500", 500));

        assertThrows(PurpleAirApiException.class, () -> registry.enrichSensor(GANEI_AYALON));

        assertEquals("Ganei-Ayalon", registry.get(GANEI_AYALON).orElseThrow().name());
    }

    @Test
    void needsAtLeastOneSensorAndNoIndexTwice() {
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensorRegistry(api, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensorRegistry(api,
                List.of(GANEI_AYALON, new PurpleAirSensor(GANEI_AYALON.sensorIndex(), "Elsewhere"))));
    }
}
