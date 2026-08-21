package com.pollution.datacollector.fetchers;

import com.pollution.common.entities.PollutionData;
import java.util.List;

/**
 * Where the collector gets its readings from. Implementations hide the
 * provider (PurpleAir, a government feed, a test fixture...) so the collector
 * only ever sees {@link PollutionData}.
 */
public interface IReadingsFetcher {

    /**
     * One-time startup work, called once before the first {@link #fetch()}:
     * e.g. loading sensor metadata. Must not throw for recoverable problems;
     * a source that cannot fully initialize should still serve what it can.
     */
    default void initialize() {
    }

    /**
     * Fetches the latest reading from every followed sensor. Sensors that fail
     * are logged and omitted, so the result may be shorter than the sensor list
     * but is never {@code null}.
     */
    List<PollutionData> fetch();
}
