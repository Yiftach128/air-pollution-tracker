package com.pollution.datacollector.testing;

import com.pollution.common.entities.PollutionData;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import java.util.List;
import java.util.Set;

/**
 * An {@link IReadingsFetcher} that returns whatever the test says the next
 * poll finds, reports as followed whatever sources the test says, and can
 * be told to fail; remembers the shard it was last given and how often it
 * was asked.
 */
public final class StubReadingsFetcher implements IReadingsFetcher {

    private List<PollutionData> next = List.of();
    private Set<String> sources = Set.of();
    private RuntimeException failure;
    private Shard shard = Shard.NONE;
    private int fetches;

    /** What every poll finds from now on. */
    public void returns(PollutionData... readings) {
        next = List.of(readings);
    }

    /** What {@link #sources()} reports from now on; nothing until told. */
    public void follows(String... sources) {
        this.sources = Set.of(sources);
    }

    /** Makes every poll throw {@code failure} until told otherwise ({@code null} to answer again). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** The shard last followed; {@link Shard#NONE} before any. */
    public Shard shard() {
        return shard;
    }

    public int fetches() {
        return fetches;
    }

    @Override
    public void follow(Shard shard) {
        this.shard = shard;
    }

    @Override
    public Set<String> sources() {
        return sources;
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
