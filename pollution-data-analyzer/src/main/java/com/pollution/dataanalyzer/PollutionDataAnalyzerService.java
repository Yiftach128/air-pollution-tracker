package com.pollution.dataanalyzer;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.dataanalyzer.analysis.RollingAverage;
import com.pollution.dataanalyzer.entities.RollingAverageSnapshot;
import com.pollution.dataanalyzer.persistence.ISnapshotStore;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Consumes the stream of {@link PollutionData} readings and maintains a
 * {@link RollingAverage} per sensor, keyed by the reading's
 * {@link PollutionData#source() source} so the sensor identity has a single
 * origin: whatever the collector published.
 * <p>
 * The averages are persisted as snapshots to an {@link ISnapshotStore} on a
 * fixed interval and once more on shutdown, and restored from it on startup.
 * Readings arrive on the subscriber's thread and persistence runs on its own
 * scheduler thread; {@link #lock} serializes both, and store I/O happens
 * outside it so it never stalls the reading stream.
 */
public class PollutionDataAnalyzerService implements AutoCloseable {

    private static final Logger logger = PollutionLogger.getLogger(PollutionDataAnalyzerService.class);

    /**
     * @param rollingAverageWindow how far back each sensor's rolling average reaches
     * @param persistInterval      how often snapshots are written to the store
     */
    public record Settings(Duration rollingAverageWindow, Duration persistInterval) {
        public Settings {
            requirePositive(rollingAverageWindow, "rollingAverageWindow");
            requirePositive(persistInterval, "persistInterval");
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive, was " + duration);
            }
        }
    }

    private final ISubscriber<PollutionData> pollutionSubscriber;
    private final ISnapshotStore snapshotStore;
    private final ScheduledExecutorService persistScheduler;
    private final Settings settings;

    private final Object lock = new Object();
    /** Guarded by {@link #lock}, as is every {@link RollingAverage} in it. */
    private final Map<String, RollingAverage> averagesBySensor = new HashMap<>();
    /** Set once restore succeeded; until then nothing is ever written to the store. */
    private volatile boolean restored;

    public PollutionDataAnalyzerService(ISubscriber<PollutionData> pollutionSubscriber,
                                        ISnapshotStore snapshotStore,
                                        ScheduledExecutorService persistScheduler,
                                        Settings settings) {
        this.pollutionSubscriber = Objects.requireNonNull(pollutionSubscriber, "pollutionSubscriber");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.persistScheduler = Objects.requireNonNull(persistScheduler, "persistScheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Restores persisted averages, subscribes to readings and starts periodic
     * persistence. Fails if the store cannot be read: starting empty would let
     * the next persist overwrite good stored state with a partial window.
     */
    public void start() {
        restore();
        restored = true;
        pollutionSubscriber.subscribe(this::handleReading);
        long persistMillis = settings.persistInterval().toMillis();
        persistScheduler.scheduleAtFixedRate(guarded("persist", this::persist), persistMillis, persistMillis, TimeUnit.MILLISECONDS);
        logger.info("subscribed to pollution readings; rolling average window {} ms, persisting every {} ms",
                settings.rollingAverageWindow().toMillis(), persistMillis);
    }

    /** An exception escaping a scheduled task silently cancels all its future runs. */
    private static Runnable guarded(String taskName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.error("{} task failed", taskName, e);
            }
        };
    }

    private void restore() {
        Collection<RollingAverageSnapshot> snapshots = snapshotStore.loadAll();
        synchronized (lock) {
            for (RollingAverageSnapshot snapshot : snapshots) {
                averagesBySensor.put(snapshot.sensorId(),
                        RollingAverage.loadFromPersistence(snapshot, settings.rollingAverageWindow()));
            }
        }
        logger.info("restored {} sensors from persistence", snapshots.size());
    }

    private void handleReading(PollutionData reading) {
        logger.debug("received {}", reading);
        RollingAverageSnapshot snapshot;
        synchronized (lock) {
            RollingAverage average = averagesBySensor.computeIfAbsent(
                    reading.source(), sensorId -> new RollingAverage(sensorId, settings.rollingAverageWindow()));
            average.addReading(reading.timestamp(), reading.value());
            snapshot = average.getSnapshot();
        }
        logger.info("sensor {}: {} average {} {} over {} samples (latest {})",
                snapshot.sensorId(), reading.pollutant().displayName(), snapshot.average().orElse(Double.NaN),
                reading.pollutant().unit(), snapshot.sampleCount(), snapshot.latest());
    }

    private void persist() {
        List<RollingAverageSnapshot> snapshots;
        synchronized (lock) {
            snapshots = averagesBySensor.values().stream().map(RollingAverage::getSnapshot).toList();
        }
        snapshotStore.saveAll(snapshots);
        logger.debug("persisted {} snapshots", snapshots.size());
    }

    /**
     * Stops the reading stream first so the final persist captures the last
     * handled reading, then releases the store.
     */
    @Override
    public void close() {
        pollutionSubscriber.close();
        shutdown(persistScheduler);
        if (restored) {
            try {
                persist();
            } catch (RuntimeException e) {
                logger.error("final persist failed", e);
            }
        }
        snapshotStore.close();
    }

    private static void shutdown(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
