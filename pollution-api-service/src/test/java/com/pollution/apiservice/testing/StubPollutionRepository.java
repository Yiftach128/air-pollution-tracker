package com.pollution.apiservice.testing;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.entities.BucketAverage;
import com.pollution.persistence.entities.ReadingsSummary;
import com.pollution.persistence.entities.SourceSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link IPollutionRepository} for the API service's tests: answers the
 * queries with whatever the test says it holds — the aggregation is the
 * database's job, not something to re-implement here — and remembers the
 * arguments of the last query, so a test can assert what was asked.
 * Can be told to fail. The API service never writes.
 */
public final class StubPollutionRepository implements IPollutionRepository {

    private List<SourceSummary> sources = List.of();
    private List<PollutionData> readings = List.of();
    private List<BucketAverage> averages = List.of();
    private List<ReadingsSummary> summary = List.of();
    private RuntimeException failure;
    private boolean closed;

    private final List<String> queries = new ArrayList<>();
    private String lastSource;
    private Instant lastFrom;
    private Instant lastTo;
    private Duration lastBucket;

    public void holdsSources(SourceSummary... sources) {
        this.sources = List.of(sources);
    }

    public void holdsReadings(PollutionData... readings) {
        this.readings = List.of(readings);
    }

    public void holdsAverages(BucketAverage... averages) {
        this.averages = List.of(averages);
    }

    public void summarizesAs(ReadingsSummary... summary) {
        this.summary = List.of(summary);
    }

    /** Makes every query throw {@code failure} until told otherwise ({@code null} to answer again). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** The names of the queries made, in order. */
    public List<String> queries() {
        return List.copyOf(queries);
    }

    public String lastSource() {
        return lastSource;
    }

    public Instant lastFrom() {
        return lastFrom;
    }

    public Instant lastTo() {
        return lastTo;
    }

    /** The bucket of the last {@code findAverages}; {@code null} if none was made. */
    public Duration lastBucket() {
        return lastBucket;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean save(PollutionData reading) {
        throw new UnsupportedOperationException("the API service never writes");
    }

    @Override
    public List<PollutionData> findReadings(String source, Pollutant pollutant, Instant from, Instant to) {
        record("findReadings(pollutant)", source, from, to);
        return readings.stream().filter(reading -> reading.pollutant() == pollutant).toList();
    }

    @Override
    public List<PollutionData> findReadings(String source, Instant from, Instant to) {
        record("findReadings", source, from, to);
        return readings;
    }

    @Override
    public List<SourceSummary> findSources() {
        failIfToldTo();
        queries.add("findSources");
        return sources;
    }

    @Override
    public List<ReadingsSummary> summarize(String source, Instant from, Instant to) {
        record("summarize", source, from, to);
        return summary;
    }

    @Override
    public List<BucketAverage> findAverages(String source, Instant from, Instant to, Duration bucket) {
        record("findAverages", source, from, to);
        lastBucket = bucket;
        return averages;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void record(String query, String source, Instant from, Instant to) {
        failIfToldTo();
        queries.add(query);
        lastSource = source;
        lastFrom = from;
        lastTo = to;
    }

    private void failIfToldTo() {
        if (failure != null) {
            throw failure;
        }
    }
}
