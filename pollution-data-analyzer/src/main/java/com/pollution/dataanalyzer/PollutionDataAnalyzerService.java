package com.pollution.dataanalyzer;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.dataanalyzer.analysis.RollingAverageGroup;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import com.pollution.dataanalyzer.persistence.IRollingAverageStateStore;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Consumes the stream of {@link PollutionData} readings and maintains a
 * {@link RollingAverageGroup} — one rolling average per configured window —
 * per series, keyed by the reading's {@link PollutionData#source() source}
 * and {@link PollutionData#pollutant() pollutant} so the sensor identity has a
 * single origin: whatever the collector published.
 * <p>
 * After every reading that changes a series, one {@link PollutionAverage}
 * carrying all of that series' window averages is published, and the series'
 * longest window is saved to an {@link IRollingAverageStateStore} (overwriting
 * the previous state). On startup every series is restored from the store,
 * shorter windows being derived from the persisted longest one. Readings
 * arrive on the subscriber's thread; {@link #lock} guards the groups, and
 * publisher and store I/O happen outside it.
 */
public class PollutionDataAnalyzerService implements AutoCloseable {

    private static final Logger logger = PollutionLogger.getLogger(PollutionDataAnalyzerService.class);

    private final ISubscriber<PollutionData> pollutionSubscriber;
    private final IPublisher<PollutionAverage> averagePublisher;
    private final IRollingAverageStateStore stateStore;
    /** Ascending; see {@link RollingAverageGroup#canonicalWindows}. */
    private final List<Duration> windows;

    private final Object lock = new Object();
    /** Guarded by {@link #lock}, as is every {@link RollingAverageGroup} in it. */
    private final Map<SensorPollutant, RollingAverageGroup> groupsBySeries = new HashMap<>();

    /**
     * @param windows the rolling-average window lengths kept for every series;
     *                not empty, all positive, no duplicates
     */
    public PollutionDataAnalyzerService(ISubscriber<PollutionData> pollutionSubscriber,
                                        IPublisher<PollutionAverage> averagePublisher,
                                        IRollingAverageStateStore stateStore,
                                        List<Duration> windows) {
        this.pollutionSubscriber = Objects.requireNonNull(pollutionSubscriber, "pollutionSubscriber");
        this.averagePublisher = Objects.requireNonNull(averagePublisher, "averagePublisher");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.windows = RollingAverageGroup.canonicalWindows(windows);
    }

    /**
     * Restores persisted averages, then subscribes to readings. Fails if the
     * store cannot be read: starting empty would let the first reading
     * overwrite good stored state with a one-sample window.
     */
    public void start() {
        restore();
        pollutionSubscriber.subscribe(this::handleReading);
        logger.info("subscribed to pollution readings; rolling average windows {}", windows);
    }

    private void restore() {
        Collection<RollingAverageState> states = stateStore.loadAll();
        synchronized (lock) {
            for (RollingAverageState state : states) {
                groupsBySeries.put(state.sensorPollutant(), RollingAverageGroup.loadFromPersistence(state, windows));
            }
        }
        logger.info("restored {} series from persistence", states.size());
    }

    private void handleReading(PollutionData reading) {
        logger.debug("received {}", reading);
        SensorPollutant series = new SensorPollutant(reading.source(), reading.pollutant());
        PollutionAverage message = null;
        RollingAverageState state = null;
        synchronized (lock) {
            RollingAverageGroup group = groupsBySeries.computeIfAbsent(series, key -> new RollingAverageGroup(key, windows));
            if (group.addReading(reading.timestamp(), reading.value())) {
                message = new PollutionAverage(
                        reading.city(), series.sensorId(), series.pollutant(), group.averages(), group.latest());
                state = group.getPersistentState();
            }
        }
        if (message == null) {
            logger.debug("{}: reading at {} is outside every window; nothing changed", series, reading.timestamp());
            return;
        }
        publish(message);
        persist(state);
    }

    /**
     * A failed publish is logged rather than propagated: the in-memory state
     * is already up to date, and the series' next reading publishes fresh averages.
     */
    private void publish(PollutionAverage message) {
        try {
            averagePublisher.send(message, message.source());
            logger.info("published {}", message);
        } catch (RuntimeException e) {
            logger.error("failed to publish {}", message, e);
        }
    }

    /**
     * A failed save is logged rather than propagated: the in-memory state is
     * already up to date, and the series' next reading saves the full state again.
     */
    private void persist(RollingAverageState state) {
        try {
            stateStore.save(state);
        } catch (RuntimeException e) {
            logger.error("{}: failed to persist rolling average state", state.sensorPollutant(), e);
        }
    }

    @Override
    public void close() {
        pollutionSubscriber.close();
        averagePublisher.close();
        stateStore.close();
    }
}
