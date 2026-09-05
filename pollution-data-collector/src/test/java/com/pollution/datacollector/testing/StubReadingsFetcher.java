package com.pollution.datacollector.testing;

import com.pollution.common.entities.PollutionData;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import java.util.List;

/**
 * An {@link IReadingsFetcher} that returns whatever the test says the next
 * poll finds, and can be told to fail; remembers whether it was initialized
 * and how often it was asked.
 */
public final class StubReadingsFetcher implements IReadingsFetcher {

    private List<PollutionData> next = List.of();
    private RuntimeException failure;
    private boolean initialized;
    private int fetches;

    /** What every poll finds from now on. */
    public void returns(PollutionData... readings) {
        next = List.of(readings);
    }

    /** Makes every poll throw {@code failure} until told otherwise ({@code null} to answer again). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int fetches() {
        return fetches;
    }

    @Override
    public void initialize() {
        initialized = true;
    }

    @Override
    public List<PollutionData> fetch() {
        fetches++;
        if (failure != null) {
            throw failure;
        }
        return next;
    }
}
