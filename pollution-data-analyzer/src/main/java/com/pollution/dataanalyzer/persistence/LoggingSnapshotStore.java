package com.pollution.dataanalyzer.persistence;

import com.pollution.common.PollutionLogger;
import com.pollution.common.json.JsonSupport;
import com.pollution.dataanalyzer.entities.RollingAverageSnapshot;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;

/**
 * A store that persists nothing: it logs the JSON it would have written and
 * restores nothing. Stands in until a real store (e.g. Redis) is wired in.
 */
public final class LoggingSnapshotStore implements ISnapshotStore {

    private static final Logger logger = PollutionLogger.getLogger(LoggingSnapshotStore.class);

    @Override
    public void saveAll(Collection<RollingAverageSnapshot> snapshots) {
        for (RollingAverageSnapshot snapshot : snapshots) {
            logger.info("would persist sensor {}: {}", snapshot.sensorId(), JsonSupport.toJson(snapshot));
        }
        logger.info("would persist {} snapshots", snapshots.size());
    }

    @Override
    public Collection<RollingAverageSnapshot> loadAll() {
        logger.info("no snapshot persistence configured; starting with no restored averages");
        return List.of();
    }

    @Override
    public void close() {
    }
}
