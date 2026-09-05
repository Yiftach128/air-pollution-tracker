package com.pollution.apiservice.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.apiservice.entities.HistoryPoint;
import com.pollution.apiservice.entities.SourceHistory;
import com.pollution.apiservice.testing.StubPollutionRepository;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.PollutionRepositoryException;
import com.pollution.persistence.entities.BucketAverage;
import com.pollution.persistence.entities.ReadingsSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryQueryTest {

    private static final Instant TO = Instant.parse("2026-09-05T10:00:00Z");
    private static final String SOURCE = "purpleair:Ganei-Ayalon";
    private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
    private static final Duration MAX_RANGE = Duration.ofDays(365);
    private static final Duration HOUR = Duration.ofHours(1);

    private final StubPollutionRepository repository = new StubPollutionRepository();
    private final HistoryQuery query = new HistoryQuery(repository, DEFAULT_RANGE, MAX_RANGE);

    private static PollutionData reading(double value, Instant at) {
        return new PollutionData("Tel Aviv", SOURCE, Pollutant.PM2_5, value, at);
    }

    @Test
    void anOpenStartReachesBackTheDefaultRangeFromTheEnd() {
        SourceHistory history = query.history(SOURCE, null, TO, null);

        assertEquals(TO.minus(DEFAULT_RANGE), history.from());
        assertEquals(TO, history.to());
        assertEquals(TO.minus(DEFAULT_RANGE), repository.lastFrom());
        assertEquals(TO, repository.lastTo());
    }

    @Test
    void anOpenEndIsNow() {
        Instant before = Instant.now();

        SourceHistory history = query.history(SOURCE, null, null, null);

        assertFalse(history.to().isBefore(before));
        assertFalse(history.to().isAfter(Instant.now()));
        assertEquals(history.to().minus(DEFAULT_RANGE), history.from());
    }

    @Test
    void withoutABucketEveryReadingIsAPoint() {
        PollutionData first = reading(10, TO.minus(HOUR));
        PollutionData second = reading(12, TO.minusSeconds(60));
        repository.holdsReadings(first, second);

        SourceHistory history = query.history(SOURCE, TO.minus(HOUR), TO, null);

        assertNull(history.bucket());
        assertEquals(List.of(HistoryPoint.of(first), HistoryPoint.of(second)), history.points());
        assertEquals(List.of("findReadings", "summarize"), repository.queries());
        assertEquals(SOURCE, repository.lastSource());
    }

    @Test
    void withABucketTheAveragesArePoints() {
        BucketAverage average = new BucketAverage(Pollutant.PM2_5, TO.minus(HOUR), 11, 10, 12, 6);
        repository.holdsAverages(average);

        SourceHistory history = query.history(SOURCE, TO.minus(HOUR), TO, Duration.ofMinutes(10));

        assertEquals(Duration.ofMinutes(10), history.bucket());
        assertEquals(List.of(HistoryPoint.of(average)), history.points());
        assertEquals(List.of("findAverages", "summarize"), repository.queries());
        assertEquals(Duration.ofMinutes(10), repository.lastBucket());
    }

    @Test
    void theRangeIsAlwaysSummarized() {
        ReadingsSummary summary = new ReadingsSummary(Pollutant.PM2_5, 11, 10, 12, 60);
        repository.summarizesAs(summary);

        SourceHistory history = query.history(SOURCE, TO.minus(HOUR), TO, null);

        assertEquals(List.of(summary), history.summary());
        assertEquals(SOURCE, history.source());
    }

    @Test
    void refusesAnEmptyOrBackwardsRange() {
        assertThrows(IllegalArgumentException.class, () -> query.history(SOURCE, TO, TO, null));
        assertThrows(IllegalArgumentException.class, () -> query.history(SOURCE, TO.plusSeconds(1), TO, null));
        assertEquals(List.of(), repository.queries());
    }

    @Test
    void refusesARangeLongerThanTheMaximumAndAllowsTheMaximumItself() {
        assertThrows(IllegalArgumentException.class, () -> query.history(SOURCE, TO.minus(MAX_RANGE).minusSeconds(1), TO, null));
        assertEquals(List.of(), repository.queries());

        query.history(SOURCE, TO.minus(MAX_RANGE), TO, null);
        assertTrue(repository.queries().contains("findReadings"));
    }

    @Test
    void refusesANonPositiveBucket() {
        assertThrows(IllegalArgumentException.class, () -> query.history(SOURCE, TO.minus(HOUR), TO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> query.history(SOURCE, TO.minus(HOUR), TO, Duration.ofMinutes(-1)));
    }

    @Test
    void aRepositoryFailureReachesTheCaller() {
        repository.failWith(new PollutionRepositoryException("postgres is down", null));

        assertThrows(PollutionRepositoryException.class, () -> query.history(SOURCE, TO.minus(HOUR), TO, null));
    }

    @Test
    void theLimitsMustMakeSense() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(repository, Duration.ZERO, MAX_RANGE));
        assertThrows(IllegalArgumentException.class, () -> new HistoryQuery(repository, DEFAULT_RANGE, DEFAULT_RANGE.minusSeconds(1)));
    }
}
