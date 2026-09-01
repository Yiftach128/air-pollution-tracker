package com.pollution.apiservice.queries;

import com.pollution.apiservice.entities.SourceStatus;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.entities.SourceSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Answers "what is every source reading right now" by joining the two
 * stores: the repository says which sources exist and when each last
 * reported, the current-reading store says what the ones still reporting
 * read now. A source known to only one store is listed anyway — one whose
 * current reading expired shows as reporting nothing, one whose reading
 * has not reached the repository yet still shows.
 * <p>
 * Throws the stores' exceptions through; a view that cannot see one store
 * would be wrong, not just incomplete.
 */
public final class SourceOverviewQuery {

    private final IPollutionRepository repository;
    private final ILatestReadingStore latestReadings;

    public SourceOverviewQuery(IPollutionRepository repository, ILatestReadingStore latestReadings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.latestReadings = Objects.requireNonNull(latestReadings, "latestReadings");
    }

    /** Every source, ordered by source. */
    public List<SourceStatus> overview() {
        Map<String, List<PollutionData>> currentBySource = new HashMap<>();
        for (PollutionData reading : latestReadings.findAll()) {
            currentBySource.computeIfAbsent(reading.source(), source -> new ArrayList<>()).add(reading);
        }
        List<SourceStatus> statuses = new ArrayList<>();
        for (SourceSummary summary : repository.findSources()) {
            List<PollutionData> current = currentBySource.remove(summary.source());
            statuses.add(statusOf(summary.source(), summary.city(), summary.lastReportedAt(),
                    current == null ? List.of() : current));
        }
        for (Map.Entry<String, List<PollutionData>> entry : currentBySource.entrySet()) {
            List<PollutionData> current = entry.getValue();
            statuses.add(statusOf(entry.getKey(), current.get(0).city(), Instant.MIN, current));
        }
        statuses.sort(Comparator.comparing(SourceStatus::source));
        return statuses;
    }

    private static SourceStatus statusOf(String source, String city, Instant lastReportedAt,
                                         List<PollutionData> current) {
        Instant newest = lastReportedAt;
        for (PollutionData reading : current) {
            if (reading.timestamp().isAfter(newest)) {
                newest = reading.timestamp();
            }
        }
        List<PollutionData> ordered = new ArrayList<>(current);
        ordered.sort(Comparator.comparing(PollutionData::pollutant));
        return SourceStatus.of(source, city, newest, ordered);
    }
}
