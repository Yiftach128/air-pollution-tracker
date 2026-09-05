package com.pollution.common.health;

/**
 * Answers a container platform's probes about the service, on a port of
 * its own so it is the same in every service whatever else the service
 * serves:
 * <ul>
 *   <li>liveness, {@code GET /healthz} — 200 while the process runs;</li>
 *   <li>readiness, {@code GET /readyz} — 200 from {@link #markReady()} until
 *       {@link #markNotReady()}, 503 otherwise. Ready means started: the
 *       service has restored its state, subscribed, begun listening. It says
 *       nothing about the stores behind the service.</li>
 * </ul>
 * Created stopped. An application starts it first, so liveness answers
 * during a slow start, marks it ready once its service runs, and in its
 * shutdown hook marks it not ready before closing anything, closing the
 * health server last.
 */
public interface IHealthServer extends AutoCloseable {

    /** Begins answering; liveness is up from here, readiness is down until {@link #markReady()}. */
    void start();

    /** From now on readiness answers 200. */
    void markReady();

    /** From now on readiness answers 503. */
    void markNotReady();

    /** Stops answering and releases the port and threads. */
    @Override
    void close();
}
