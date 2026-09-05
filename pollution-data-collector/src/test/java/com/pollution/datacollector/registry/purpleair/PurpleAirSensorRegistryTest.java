package com.pollution.datacollector.registry.purpleair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
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

    private final FakePurpleAirSensorApi api = new FakePurpleAirSensorApi();
    private final ISensorRegistry<PurpleAirSensor, PurpleAirSensorInfo> registry =
            new PurpleAirSensorRegistry(api, List.of(GANEI_AYALON, SHOHAM));

    @Test
    void followsTheGivenSensorsInOrder() {
        assertEquals(List.of(GANEI_AYALON, SHOHAM), registry.sensors());
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
    void enrichingAllSkipsTheSensorsThatFailAndKeepsGoing() {
        api.failsInfo(GANEI_AYALON.sensorIndex(), new PurpleAirApiException("PurpleAir returned HTTP 500", 500));
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        registry.enrichAll();

        assertEquals(Optional.empty(), registry.get(GANEI_AYALON));
        assertTrue(registry.get(SHOHAM).isPresent());
        assertEquals(List.of(GANEI_AYALON.sensorIndex(), SHOHAM.sensorIndex()), api.infoRequests());
    }

    @Test
    void needsAtLeastOneSensorAndNoIndexTwice() {
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensorRegistry(api, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensorRegistry(api,
                List.of(GANEI_AYALON, new PurpleAirSensor(GANEI_AYALON.sensorIndex(), "Elsewhere"))));
    }
}
