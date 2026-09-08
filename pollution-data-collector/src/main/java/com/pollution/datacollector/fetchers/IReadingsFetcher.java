package com.pollution.datacollector.fetchers;

import com.pollution.common.entities.PollutionData;
import com.pollution.datacollector.entities.Shard;
import java.util.List;
import java.util.Set;

/**
 * Where the collector gets its readings from. Implementations hide the
 * provider (PurpleAir, a government feed, a test fixture...) so the collector
 * only ever sees {@link PollutionData}. A fetcher reads nothing until it is
 * told which {@link Shard} of the configured sensors is this instance's.
 */
public interface IReadingsFetcher {

    /**
     * Follows the shard's share of the configured sensors from now on:
     * {@link #fetch()} reads those and no others. May do preparatory work
     * for the sensors gained (e.g. loading their metadata) but must not
     * throw for recoverable problems; a sensor that cannot be prepared is
     * still followed and served as well as it can be.
     */
    void follow(Shard shard);

    /**
     * The sources ({@link PollutionData#source()}) of the followed sensors,
     * each named as {@link #fetch()} would name it right now. Empty while
     * nothing is followed. No I/O.
     */
    Set<String> sources();

    /**
     * Fetches the latest reading from every followed sensor. Sensors that fail
     * are logged and omitted, so the result may be shorter than the sensor list
     * but is never {@code null}.
     */
    List<PollutionData> fetch();
}
