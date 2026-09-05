package com.pollution.datacollector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.testing.MutableClock;
import com.pollution.common.testing.RecordingPublisher;
import com.pollution.datacollector.PollutionDataCollectorService.Schedule;
import com.pollution.datacollector.testing.ManualScheduler;
import com.pollution.datacollector.testing.StubReadingsFetcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The collector alone: its two loops are ticked by hand, the readings come
 * from a stub fetcher, and what it publishes — and stamps — is asserted.
 * Polls every five minutes, publishes every ten seconds, drops a reading
 * not refreshed for ten minutes.
 */
class PollutionDataCollectorServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    /** When the sensor took the reading, a little before the collector polled it. */
    private static final Instant SEEN = T0.minusSeconds(40);
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration POLL = Duration.ofMinutes(5);
    private static final Duration PUBLISH = Duration.ofSeconds(10);
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final StubReadingsFetcher fetcher = new StubReadingsFetcher();
    private final RecordingPublisher<PollutionData> publisher = new RecordingPublisher<>();
    private final ManualScheduler poll = new ManualScheduler();
    private final ManualScheduler publish = new ManualScheduler();
    private final MutableClock clock = MutableClock.at(T0);
    private final PollutionDataCollectorService service = new PollutionDataCollectorService(
            fetcher, publisher, poll, publish, new Schedule(POLL, PUBLISH, MAX_AGE), clock);

    private static PollutionData reading(String source, double value, Instant seen) {
        return new PollutionData("Ganei Ayalon", source, Pollutant.PM2_5, value, seen);
    }

    private List<Instant> publishedTimestamps() {
        return publisher.messages().stream().map(PollutionData::timestamp).toList();
    }

    @Test
    void startingInitializesTheFetcherAndSchedulesBothLoops() {
        service.start();

        assertTrue(fetcher.isInitialized());
        assertEquals(List.of(new ManualScheduler.Scheduled(poll.tasks().get(0).task(), Duration.ZERO, POLL)), poll.tasks());
        assertEquals(List.of(new ManualScheduler.Scheduled(publish.tasks().get(0).task(), PUBLISH, PUBLISH)), publish.tasks());
    }

    @Test
    void theFirstPublishCarriesTheSensorsOwnTimestampKeyedBySource() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));

        poll.runAll();
        publish.runAll();

        assertEquals(List.of(new RecordingPublisher.Sent<>(reading(SOURCE, 12.5, SEEN), SOURCE)), publisher.sent());
    }

    @Test
    void everyRepublishStepsTheTimestampForwardByOnePublishInterval() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();

        publish.runAll();
        publish.runAll();
        publish.runAll();

        assertEquals(List.of(SEEN, SEEN.plus(PUBLISH), SEEN.plus(PUBLISH.multipliedBy(2))), publishedTimestamps());
    }

    @Test
    void aFreshPollStartsTheSteppingOver() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publish.runAll();
        publish.runAll();

        poll.runAll();
        publish.runAll();

        assertEquals(List.of(SEEN, SEEN.plus(PUBLISH), SEEN), publishedTimestamps());
    }

    @Test
    void nothingIsPublishedBeforeTheFirstPoll() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));

        publish.runAll();

        assertEquals(List.of(), publisher.sent());
    }

    @Test
    void everyCachedSensorIsPublishedEachTime() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN), reading("purpleair:Shoham", 8, SEEN));
        poll.runAll();

        publish.runAll();

        assertEquals(2, publisher.sent().size());
    }

    @Test
    void aReadingNotRefreshedForTheMaxAgeIsDroppedRatherThanReplayedForever() {
        service.start();
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
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        fetcher.returns();

        poll.runAll();
        publish.runAll();

        assertEquals(1, publisher.sent().size());
    }

    @Test
    void aPollThatFailsDoesNotStopTheLoop() {
        service.start();
        fetcher.failWith(new RuntimeException("PurpleAir is down"));

        poll.runAll();
        fetcher.failWith(null);
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publish.runAll();

        assertEquals(2, fetcher.fetches());
        assertEquals(1, publisher.sent().size());
    }

    @Test
    void aPublishThatFailsDoesNotStopTheLoopNorCountAsPublished() {
        service.start();
        fetcher.returns(reading(SOURCE, 12.5, SEEN));
        poll.runAll();
        publisher.failWith(new RuntimeException("kafka is down"));

        publish.runAll();
        publisher.failWith(null);
        publish.runAll();

        assertEquals(List.of(SEEN), publishedTimestamps(), "the failed publish did not step the timestamp");
    }

    @Test
    void closingShutsDownBothLoops() {
        service.start();

        service.close();

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
