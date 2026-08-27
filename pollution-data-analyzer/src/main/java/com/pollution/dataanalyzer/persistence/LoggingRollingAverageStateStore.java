package com.pollution.dataanalyzer.persistence;

import com.pollution.common.PollutionLogger;
import com.pollution.common.json.JsonSupport;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;

/**
 * A store that persists nothing: it logs the JSON it would have written and
 * restores nothing. Stands in for running without a cache.
 */
public final class LoggingRollingAverageStateStore implements IRollingAverageStateStore {

    private static final Logger logger = PollutionLogger.getLogger(LoggingRollingAverageStateStore.class);

    @Override
    public void save(RollingAverageState state) {
        logger.info("would persist {}: {}", state.sensorPollutant(), JsonSupport.toJson(state));
    }

    @Override
    public Collection<RollingAverageState> loadAll() {
        logger.info("no state persistence configured; starting with no restored averages");
        return List.of();
    }

    @Override
    public void close() {
    }
}
