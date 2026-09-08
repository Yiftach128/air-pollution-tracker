package com.pollution.datacollector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.testing.MutableClock;
import com.pollution.common.testing.RecordingPublisher;
import com.pollution.datacollector.PollutionDataCollectorService.Schedule;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.testing.ManualGroupMembership;
import com.pollution.datacollector.testing.ManualScheduler;
import com.pollution.datacollector.testing.StubReadingsFetcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The collector alone: its two loops are ticked by hand, the group is
 * played by the test, the readings come from a stub fetcher, and what it
 * publishes — and stamps — is asserted. Polls every five minutes, publishes
 * every ten seconds, drops a reading not refreshed for ten minutes.
 */
class PollutionDataCollectorServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    /** When the sensor took the reading, a little before the collector polled it. */
    private static final Instant SEEN = T0.minusSeconds(40);
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final String SHOHAM = "purpleair:Shoham";
    private static final Duration POLL = Duration.ofMinutes(5);
    private static final Duration PUBLISH = Duration.ofSeconds(10);
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final Shard ALL = new Shard(0, 1);

    private final StubReadingsFetcher fetcher = new StubReadingsFetcher();
    private final RecordingPublisher<PollutionData> publisher = new RecordingPublisher<>();
    private final ManualGroupMembership membership = new ManualGroupMembership();
    private final ManualScheduler poll = new ManualScheduler();
    private final ManualScheduler publish = new ManualScheduler();
    private final MutableClock clock = MutableClock.at(T0);
    private final PollutionDataCollectorService service = new PollutionDataCollectorService(
            fetcher, publisher, membership, poll, publish, new Schedule(POLL, PUBLISH, MAX_AGE), clock);

    private static PollutionData reading(String source, double value, Instant seen) {
        return new PollutionData("Ganei Ayalon", source, Pollutant.PM2_5, value, seen);
    }

    private List<Instant> publishedTimestamps() {
        return publisher.messages().stream().map(PollutionData::timestamp).toList();
    }

    private List<String> publishedSources() {
        return publisher.messages().stream().map(PollutionData::source).toList();
    }

    /** Starts the service and has the group give it every sensor; the poll that brings finds nothing yet. */
    private void startFollowingEverySensor() {
        fetcher.follows(SOURCE, SHOHAM);
        service.start();
        membership.assign(ALL);
    }

    @Test
    void startingSchedulesBothLoopsAndJoinsTheGroup() {
        service.start();

        assertEquals(List.of(new ManualScheduler.Scheduled(poll.tasks().get(0).task(), Duration.ZERO, POLL)), poll.tasks());
        assertEquals(List.of(new ManualScheduler.Scheduled(publish.tasks().get(0).task(), PUBLISH, PUBLISH)), publish.tasks());
        assertTrue(membership.isStarted());
        assertEquals(Shard.NONE, fetcher.shard(), "follows nothing until the group says");
    }

    @Test
    void beingGivenAShardFollowsItAndPollsItAtOnce() {
        fetcher.follows(SOURCE);
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        service.start();

        membership.assign(new Shard(0, 2));

        assertEquals(new Shard(0, 2), fetcher.shard());
        assertEquals(1, fetcher.fetches(), "polled on assignment, not at the next scheduled poll");
        publish.runAll();
        assertEquals(List.of(SOURCE), publishedSources());
    }

    @Test
    void nothingIsPolledWhileTheShardHoldsNoSensor() {
        service.start();

        poll.runAll();
        membership.assign(new Shard(2, 3));
        poll.runAll();

        assertEquals(0, fetcher.fetches());
    }

    @Test
    void aShardChangeDropsTheCachedReadingsOfSensorsNoLongerFollowed() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN), reading(SHOHAM, 8, SEEN));
        poll.runAll();

        fetcher.follows(SHOHAM);
        fetcher.returns(reading(SHOHAM, 8, SEEN));
        membership.assign(new Shard(1, 2));
        publish.runAll();

        assertEquals(List.of(SHOHAM), publishedSources(), "the other sensor is someone else's now");
    }

    @Test
    void losingTheShardStopsPollingAndPublishing() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        int fetches = fetcher.fetches();

        fetcher.follows();
        membership.assign(Shard.NONE);
        publish.runAll();
        poll.runAll();

        assertEquals(List.of(), publisher.sent());
        assertEquals(fetches, fetcher.fetches());
    }

    @Test
    void theFirstPublishCarriesTheSensorsOwnTimestampKeyedBySource() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));

        poll.runAll();
        publish.runAll();

        assertEquals(List.of(new RecordingPublisher.Sent<>(reading(SOURCE, 12.5, SEEN), SOURCE)), publisher.sent());
    }

    @Test
    void everyRepublishStepsTheTimestampForwardByOnePublishInterval() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();

        publish.runAll();
        publish.runAll();
        publish.runAll();

        assertEquals(List.of(SEEN, SEEN.plus(PUBLISH), SEEN.plus(PUBLISH.multipliedBy(2))), publishedTimestamps());
    }

    @Test
    void aFreshPollStartsTheSteppingOver() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publish.runAll();
        publish.runAll();

        poll.runAll();
        publish.runAll();

        assertEquals(List.of(SEEN, SEEN.plus(PUBLISH), SEEN), publishedTimestamps());
    }

    @Test
    void nothingIsPublishedBeforeTheFirstPollThatFindsAReading() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));

        publish.runAll();

        assertEquals(List.of(), publisher.sent());
    }

    @Test
    void everyCachedSensorIsPublishedEachTime() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN), reading(SHOHAM, 8, SEEN));
        poll.runAll();

        publish.runAll();

        assertEquals(2, publisher.sent().size());
    }

    @Test
    void aReadingNotRefreshedForTheMaxAgeIsDroppedRatherThanReplayedForever() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();

        clock.advance(MAX_AGE);
        publish.runAll();
        assertEquals(1, publisher.sent().size(), "exactly the max age old is still published");

        clock.advance(Duration.ofSeconds(1));
        publish.runAll();
        publish.runAll();
        assertEquals(1, publisher.sent().size(), "older than the max age is dropped");

        poll.runAll();
        publish.runAll();
        assertEquals(List.of(SEEN, SEEN), publishedTimestamps(), "a fresh poll brings it back from the start");
    }

    @Test
    void anEmptyPollKeepsWhatIsCachedUntilItAges() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        fetcher.returns();

        poll.runAll();
        publish.runAll();

        assertEquals(1, publisher.sent().size());
    }

    @Test
    void aPollThatFailsDoesNotStopTheLoop() {
        startFollowingEverySensor();
        fetcher.failWith(new RuntimeException("PurpleAir is down"));

        poll.runAll();
        fetcher.failWith(null);
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publish.runAll();

        assertEquals(3, fetcher.fetches(), "the poll on assignment, the failed one and the one after");
        assertEquals(1, publisher.sent().size());
    }

    @Test
    void aPublishThatFailsDoesNotStopTheLoopNorCountAsPublished() {
        startFollowingEverySensor();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publisher.failWith(new RuntimeException("kafka is down"));

        publish.runAll();
        publisher.failWith(null);
        publish.runAll();

        assertEquals(List.of(SEEN), publishedTimestamps(), "the failed publish did not step the timestamp");
    }

    @Test
    void closingLeavesTheGroupAndShutsDownBothLoops() {
        service.start();

        service.close();

        assertTrue(membership.isClosed());
        assertTrue(poll.isShutdown());
        assertTrue(publish.isShutdown());
    }

    @Test
    void theScheduleMustBePositiveAndLetAReadingOutliveOnePoll() {
        assertThrows(IllegalArgumentException.class, () -> new Schedule(Duration.ZERO, PUBLISH, MAX_AGE));
        assertThrows(IllegalArgumentException.class, () -> new Schedule(POLL, Duration.ofSeconds(-1), MAX_AGE));
        assertThrows(IllegalArgumentException.class, () -> new Schedule(POLL, PUBLISH, null));
        assertThrows(IllegalArgumentException.class, () -> new Schedule(POLL, PUBLISH, POLL.minusSeconds(1)));
        new Schedule(POLL, PUBLISH, POLL);
    }
}
