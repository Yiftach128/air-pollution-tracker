package com.pollution.dataanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.entities.WindowAverage;
import com.pollution.common.testing.ManualSubscriber;
import com.pollution.common.testing.RecordingPublisher;
import com.pollution.dataanalyzer.entities.Reading;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import com.pollution.dataanalyzer.persistence.RecordingStateStore;
import com.pollution.persistence.PollutionCacheException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The analyzer alone: readings are delivered into it by hand, and the
 * averages it publishes and the state it saves are asserted. Windows are
 * ten minutes and one hour.
 */
class PollutionDataAnalyzerServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final String CITY = "Tel Aviv";
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final SensorPollutant SERIES = new SensorPollutant(SOURCE, Pollutant.PM2_5);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final List<Duration> WINDOWS = List.of(TEN_MINUTES, HOUR);

    private final ManualSubscriber<PollutionData> readings = new ManualSubscriber<>();
    private final RecordingPublisher<PollutionAverage> averages = new RecordingPublisher<>();
    private final RecordingStateStore store = new RecordingStateStore();
    private final PollutionDataAnalyzerService service = new PollutionDataAnalyzerService(readings, averages, store, WINDOWS);

    private static Instant minutes(long minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private static PollutionData reading(Instant at, double value) {
        return new PollutionData(CITY, SOURCE, Pollutant.PM2_5, value, at);
    }

    private static PollutionAverage averageOf(Instant at, WindowAverage... windows) {
        return new PollutionAverage(CITY, SOURCE, Pollutant.PM2_5, List.of(windows), at);
    }

    @Test
    void aReadingPublishesTheSeriesAverageOverEveryWindowKeyedBySource() {
        service.start();

        readings.deliver(reading(minutes(0), 10));

        PollutionAverage expected = averageOf(minutes(0),
                new WindowAverage(TEN_MINUTES, 10, 1), new WindowAverage(HOUR, 10, 1));
        assertEquals(List.of(new RecordingPublisher.Sent<>(expected, SOURCE)), averages.sent());
    }

    @Test
    void theAveragesFollowTheReadings() {
        service.start();

        readings.deliver(reading(minutes(0), 10));
        readings.deliver(reading(minutes(5), 30));
        readings.deliver(reading(minutes(12), 50));

        assertEquals(averageOf(minutes(12), new WindowAverage(TEN_MINUTES, 40, 2), new WindowAverage(HOUR, 30, 3)),
                averages.messages().get(2));
    }

    @Test
    void theLongestWindowIsSavedAfterEveryChange() {
        service.start();

        readings.deliver(reading(minutes(0), 10));
        readings.deliver(reading(minutes(5), 30));

        assertEquals(2, store.saved().size());
        RollingAverageState last = store.saved().get(1);
        assertEquals(SERIES, last.sensorPollutant());
        assertEquals(HOUR, last.window());
        assertEquals(List.of(new Reading(minutes(0), 10), new Reading(minutes(5), 30)), last.readings());
        assertEquals(minutes(5), last.latest());
    }

    @Test
    void aRepeatedReadingChangesNothing() {
        service.start();

        readings.deliver(reading(minutes(0), 10));
        readings.deliver(reading(minutes(0), 10));
        readings.deliver(reading(minutes(-1), 10));

        assertEquals(1, averages.sent().size());
        assertEquals(1, store.saved().size());
    }

    @Test
    void seriesAreKeptApartBySourceAndPollutant() {
        service.start();

        readings.deliver(reading(minutes(0), 10));
        readings.deliver(new PollutionData(CITY, "purpleair:Shoham", Pollutant.PM2_5, 20, minutes(0)));
        readings.deliver(new PollutionData(CITY, SOURCE, Pollutant.PM10, 30, minutes(0)));

        assertEquals(3, averages.sent().size());
        for (PollutionAverage average : averages.messages()) {
            assertEquals(1, average.averages().get(0).sampleCount(), average.toString());
        }
        assertEquals(List.of(SOURCE, "purpleair:Shoham", SOURCE), averages.sent().stream().map(RecordingPublisher.Sent::key).toList());
    }

    @Test
    void persistedStateIsContinuedNotStartedOver() {
        store.holds(new RollingAverageState(SERIES, HOUR, 40, minutes(5),
                List.of(new Reading(minutes(0), 10), new Reading(minutes(5), 30))));
        service.start();

        readings.deliver(reading(minutes(12), 50));

        assertEquals(List.of(averageOf(minutes(12), new WindowAverage(TEN_MINUTES, 40, 2), new WindowAverage(HOUR, 30, 3))),
                averages.messages());
    }

    @Test
    void aReadingNotNewerThanThePersistedNewestIsIgnored() {
        store.holds(new RollingAverageState(SERIES, HOUR, 40, minutes(5),
                List.of(new Reading(minutes(0), 10), new Reading(minutes(5), 30))));
        service.start();

        readings.deliver(reading(minutes(5), 30));

        assertEquals(List.of(), averages.sent());
        assertEquals(List.of(), store.saved());
    }

    @Test
    void startFailsWhenThePersistedStateCannotBeRead() {
        store.failLoadsWith(new PollutionCacheException("redis is down", null));

        assertThrows(PollutionCacheException.class, service::start);
        assertFalse(readings.isSubscribed());
    }

    @Test
    void aFailedPublishStillSavesTheState() {
        service.start();
        averages.failWith(new RuntimeException("kafka is down"));

        readings.deliver(reading(minutes(0), 10));

        assertEquals(1, store.saved().size());
    }

    @Test
    void aFailedSaveDoesNotStopTheNextReading() {
        service.start();
        store.failSavesWith(new PollutionCacheException("redis is down", null));

        readings.deliver(reading(minutes(0), 10));
        store.failSavesWith(null);
        readings.deliver(reading(minutes(1), 20));

        assertEquals(2, averages.sent().size());
        assertEquals(1, store.saved().size());
        assertEquals(2, store.saved().get(0).sampleCount(), "the next save carries the full state");
    }

    @Test
    void closingClosesEverythingItOwns() {
        service.close();

        assertTrue(readings.isClosed());
        assertTrue(averages.isClosed());
        assertTrue(store.isClosed());
    }

    @Test
    void needsAtLeastOneWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new PollutionDataAnalyzerService(readings, averages, store, List.of()));
    }
}
