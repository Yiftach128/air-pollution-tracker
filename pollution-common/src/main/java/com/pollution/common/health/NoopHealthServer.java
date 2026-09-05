package com.pollution.common.health;

import com.pollution.common.PollutionLogger;
import org.slf4j.Logger;

/**
 * The {@link IHealthServer} of a service nobody probes — a process on a
 * developer's machine — so applications need no special case for a missing
 * port: it listens on nothing and only says so once.
 */
public final class NoopHealthServer implements IHealthServer {

    private static final Logger logger = PollutionLogger.getLogger(NoopHealthServer.class);

    @Override
    public void start() {
        logger.info("no health server: HEALTH_PORT is not set");
    }

    @Override
    public void markReady() {
    }

    @Override
    public void markNotReady() {
    }

    @Override
    public void close() {
    }
}
