package com.pollution.apiservice.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.apiservice.entities.SourceStatus;
import com.pollution.apiservice.testing.StubLatestReadingStore;
import com.pollution.apiservice.testing.StubPollutionRepository;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.PollutionRepositoryException;
import com.pollution.persistence.entities.SourceSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceOverviewQueryTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final String GANEI_AYALON = "purpleair:Ganei-Ayalon";
    private static final String SHOHAM = "purpleair:Shoham";

    private final StubPollutionRepository repository = new StubPollutionRepository();
    private final StubLatestReadingStore latest = new StubLatestReadingStore();
    private final SourceOverviewQuery query = new SourceOverviewQuery(repository, latest);

    private static PollutionData reading(String source, Pollutant pollutant, double value, Instant at) {
        return new PollutionData("Tel Aviv", source, pollutant, value, at);
    }

    @Test
    void joinsWhatTheRepositoryKnowsWithWhatIsCurrent() {
        repository.holdsSources(new SourceSummary(GANEI_AYALON, "Tel Aviv", T0));
        PollutionData current = reading(GANEI_AYALON, Pollutant.PM2_5, 12.5, T1);
        latest.holds(current);

        List<SourceStatus> overview = query.overview();

        assertEquals(List.of(new SourceStatus(GANEI_AYALON, "purpleair", "Ganei-Ayalon", "Tel Aviv", T1, List.of(current))), overview);
    }

    @Test
    void aSourceThatStoppedReportingIsListedWithNoCurrentReading() {
        repository.holdsSources(new SourceSummary(GANEI_AYALON, "Tel Aviv", T0));

        List<SourceStatus> overview = query.overview();

        assertEquals(1, overview.size());
        assertEquals(List.of(), overview.get(0).currentReadings());
        assertEquals(T0, overview.get(0).lastReportedAt());
    }

    @Test
    void aSourceOnlyTheCurrentStoreKnowsIsListedFromItsReading() {
        PollutionData current = reading(SHOHAM, Pollutant.PM2_5, 8, T1);
        latest.holds(current);

        List<SourceStatus> overview = query.overview();

        assertEquals(List.of(SourceStatus.of(SHOHAM, "Tel Aviv", T1, List.of(current))), overview);
    }

    @Test
    void lastReportedIsTheNewerOfTheRepositorysAndTheCurrentReadings() {
        repository.holdsSources(new SourceSummary(GANEI_AYALON, "Tel Aviv", T1));
        latest.holds(reading(GANEI_AYALON, Pollutant.PM2_5, 12.5, T0));

        assertEquals(T1, query.overview().get(0).lastReportedAt());
    }

    @Test
    void sourcesAreOrderedBySourceAndReadingsByPollutant() {
        repository.holdsSources(new SourceSummary(SHOHAM, "Shoham", T0), new SourceSummary(GANEI_AYALON, "Tel Aviv", T0));
        PollutionData pm10 = reading(GANEI_AYALON, Pollutant.PM10, 20, T0);
        PollutionData pm25 = reading(GANEI_AYALON, Pollutant.PM2_5, 12.5, T0);
        latest.holds(pm10, pm25);

        List<SourceStatus> overview = query.overview();

        assertEquals(List.of(GANEI_AYALON, SHOHAM), overview.stream().map(SourceStatus::source).toList());
        assertEquals(List.of(pm25, pm10), overview.get(0).currentReadings());
    }

    @Test
    void anIdentifierThatIsNotASourceIdIsShownWhole() {
        repository.holdsSources(new SourceSummary("308702", "Tel Aviv", T0));

        SourceStatus status = query.overview().get(0);

        assertNull(status.provider());
        assertEquals("308702", status.sensor());
    }

    @Test
    void aFailingStoreFailsTheWholeOverview() {
        latest.failWith(new PollutionCacheException("redis is down", null));
        assertThrows(PollutionCacheException.class, query::overview);

        latest.failWith(null);
        repository.failWith(new PollutionRepositoryException("postgres is down", null));
        assertThrows(PollutionRepositoryException.class, query::overview);
    }
}
