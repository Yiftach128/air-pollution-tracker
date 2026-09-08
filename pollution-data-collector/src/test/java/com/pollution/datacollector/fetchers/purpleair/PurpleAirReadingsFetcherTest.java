package com.pollution.datacollector.fetchers.purpleair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.SourceId;
import com.pollution.datacollector.api.purpleair.PurpleAirApiException;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensor;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.registry.purpleair.PurpleAirSensorRegistry;
import com.pollution.datacollector.testing.FakePurpleAirSensorApi;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PurpleAirReadingsFetcherTest {

    private static final Instant LAST_SEEN = Instant.parse("2026-09-05T10:00:00Z");
    private static final PurpleAirSensor GANEI_AYALON = new PurpleAirSensor(308702, "Ganei Ayalon");
    private static final PurpleAirSensor SHOHAM = new PurpleAirSensor(298123, "Shoham");
    private static final Shard ALL = new Shard(0, 1);

    private final FakePurpleAirSensorApi api = new FakePurpleAirSensorApi();
    private final PurpleAirSensorRegistry registry = new PurpleAirSensorRegistry(api, List.of(GANEI_AYALON, SHOHAM));
    private final IReadingsFetcher fetcher = new PurpleAirReadingsFetcher(api, registry);

    @Test
    void nothingIsFetchedUntilAShardIsFollowed() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);

        assertEquals(List.of(), fetcher.fetch());
        assertEquals(Set.of(), fetcher.sources());
        assertEquals(List.of(), api.readingRequests());
    }

    @Test
    void followingAShardEnrichesItsSensors() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        fetcher.follow(ALL);

        assertTrue(registry.get(GANEI_AYALON).isPresent());
        assertTrue(registry.get(SHOHAM).isPresent());
    }

    @Test
    void onlyTheFollowedShareIsFetched() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN);
        fetcher.follow(new Shard(1, 2));

        List<PollutionData> readings = fetcher.fetch();

        assertEquals(List.of(new PollutionData("Shoham", "purpleair:Shoham-Park", Pollutant.PM2_5, 8, LAST_SEEN)), readings);
        assertEquals(List.of(SHOHAM.sensorIndex()), api.readingRequests());
    }

    @Test
    void aReadingIsNamedAfterTheSensorsPurpleAirNameAndPlacedInOurCity() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN.minusSeconds(30));
        fetcher.follow(ALL);

        List<PollutionData> readings = fetcher.fetch();

        assertEquals(List.of(
                new PollutionData("Ganei Ayalon", "purpleair:Ganei-Ayalon", Pollutant.PM2_5, 12.5, LAST_SEEN),
                new PollutionData("Shoham", "purpleair:Shoham-Park", Pollutant.PM2_5, 8, LAST_SEEN.minusSeconds(30))), readings);
    }

    @Test
    void everySourceIsASourceIdOfTheProviderPurpleair() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN);
        fetcher.follow(ALL);

        for (PollutionData reading : fetcher.fetch()) {
            assertEquals("purpleair", SourceId.parse(reading.source()).provider());
        }
    }

    @Test
    void aSensorThatCouldNotBeEnrichedIsNamedByItsIndexUntilEnrichmentSucceeds() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.failsInfo(SHOHAM.sensorIndex(), new PurpleAirApiException("PurpleAir returned HTTP 500", 500));
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN);
        fetcher.follow(ALL);

        assertEquals("purpleair:298123", fetcher.fetch().get(1).source());

        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");

        assertEquals("purpleair:Shoham-Park", fetcher.fetch().get(1).source());
        assertEquals(List.of(308702, 298123, 298123, 298123), api.infoRequests(), "retried on every poll until it succeeds");
    }

    @Test
    void theSourcesAreTheFollowedSensorsAsAFetchWouldNameThemNowWithoutAskingPurpleAir() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.failsInfo(SHOHAM.sensorIndex(), new PurpleAirApiException("PurpleAir returned HTTP 500", 500));
        fetcher.follow(ALL);

        assertEquals(Set.of("purpleair:Ganei-Ayalon", "purpleair:298123"), fetcher.sources());
        assertEquals(List.of(308702, 298123), api.infoRequests(), "only the follow asked; sources() did not");

        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");
        api.reads(GANEI_AYALON.sensorIndex(), 12.5, LAST_SEEN);
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN);
        fetcher.fetch();

        assertEquals(Set.of("purpleair:Ganei-Ayalon", "purpleair:Shoham-Park"), fetcher.sources());
    }

    @Test
    void aSensorThatCannotBeReadIsLeftOutAndTheOthersStillCome() {
        api.knows(GANEI_AYALON.sensorIndex(), "Ganei-Ayalon");
        api.knows(SHOHAM.sensorIndex(), "Shoham-Park");
        api.failsReading(GANEI_AYALON.sensorIndex(), new PurpleAirApiException("request for sensor 308702 failed", new IOException()));
        api.reads(SHOHAM.sensorIndex(), 8, LAST_SEEN);
        fetcher.follow(ALL);

        List<PollutionData> readings = fetcher.fetch();

        assertEquals(1, readings.size());
        assertEquals("purpleair:Shoham-Park", readings.get(0).source());
    }
}
