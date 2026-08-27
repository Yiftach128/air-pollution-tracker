package com.pollution.dataanalyzer.analysis;

import com.pollution.common.PollutionLogger;
import com.pollution.dataanalyzer.entities.Reading;
import com.pollution.dataanalyzer.entities.RollingAverageSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import org.slf4j.Logger;

/**
 * A time-windowed rolling average of one sensor's readings.
 * <p>
 * Readings are appended in arrival order; every {@link #addReading} evicts
 * readings older than {@code window}, measured back from the newest timestamp
 * seen so far, so the buffer only ever holds the current window. A running
 * sum keeps the average O(1) to compute.
 * <p>
 * Readings are expected to arrive roughly in timestamp order. A reading that
 * arrives already outside the window is dropped; one that arrives late but
 * still inside the window is kept, and may linger slightly past its expiry
 * because eviction only inspects the head of the queue.
 * <p>
 * Invariant: the reading carrying the newest timestamp is never evicted (it
 * cannot be before its own cutoff), so the buffer is empty exactly when no
 * reading has ever been accepted.
 * <p>
 * Not thread-safe: callers must serialize access. The analyzer service holds
 * one lock around every instance it owns.
 */
public class RollingAverage {

    private static final Logger logger = PollutionLogger.getLogger(RollingAverage.class);

    private final String sensorId;
    private final Duration window;
    private final Deque<Reading> buffer = new ArrayDeque<>();
    private double sum;
    /** Newest timestamp seen so far; the window is measured back from it. Null until the first reading. */
    private Instant latest;

    /**
     * @param sensorId identifier of the sensor whose readings are averaged
     * @param window   how far back from the newest reading the window reaches; must be positive
     */
    public RollingAverage(String sensorId, Duration window) {
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
    }

    /**
     * Rebuilds a rolling average from a persisted snapshot.
     * <p>
     * The configured window wins over the persisted one: the snapshot's
     * readings are replayed in order and any that fall outside
     * {@code latest - configuredWindow} are dropped, so the restored buffer and
     * sum are exactly what this window would have kept.
     *
     * @param snapshot         state saved by {@link #getSnapshot()}
     * @param configuredWindow the window the restored instance should use
     */
    public static RollingAverage loadFromPersistence(RollingAverageSnapshot snapshot, Duration configuredWindow) {
        Objects.requireNonNull(snapshot, "snapshot");
        RollingAverage restored = new RollingAverage(snapshot.sensorId(), configuredWindow);
        if (!configuredWindow.equals(snapshot.window())) {
            logger.warn("sensor {}: persisted window {} differs from configured {}; readings outside the configured window are dropped",
                    snapshot.sensorId(), snapshot.window(), configuredWindow);
        }
        restored.latest = snapshot.latest();
        for (Reading reading : snapshot.readings()) {
            restored.addReading(reading.timestamp(), reading.value());
        }
        logger.info("sensor {}: restored {} of {} readings, latest {}",
                snapshot.sensorId(), restored.size(), snapshot.sampleCount(), restored.latest);
        return restored;
    }

    /**
     * Appends a reading to the window and drops every reading that has fallen
     * out of it, i.e. is older than {@code latest - window} where {@code latest}
     * is the newest timestamp seen so far. A reading that is itself already
     * older than that cutoff is ignored.
     */
    public void addReading(Instant timestamp, double value) {
        Objects.requireNonNull(timestamp, "timestamp");
        if (latest == null || timestamp.isAfter(latest)) {
            latest = timestamp;
        }
        Instant cutoff = latest.minus(window);
        if (timestamp.isBefore(cutoff)) {
            logger.debug("sensor {}: dropping late reading at {}, window starts at {}", sensorId, timestamp, cutoff);
            return;
        }

        buffer.add(new Reading(timestamp, value));
        sum += value;
        while (buffer.peek().timestamp().isBefore(cutoff)) {
            sum -= buffer.poll().value();
        }
    }

    /**
     * The mean of the readings currently in the window, or empty if no reading
     * has been added yet.
     */
    public OptionalDouble calculateAverage() {
        if (buffer.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sum / buffer.size());
    }

    /**
     * The complete current state, sufficient to rebuild this instance with
     * {@link #loadFromPersistence}. The returned snapshot is immutable and
     * does not change when this average does.
     */
    public RollingAverageSnapshot getSnapshot() {
        return new RollingAverageSnapshot(sensorId, window, sum, latest, List.copyOf(buffer));
    }

    /** Identifier of the sensor whose readings are averaged. */
    public String sensorId() {
        return sensorId;
    }

    /** How far back from the newest reading the window reaches. */
    public Duration window() {
        return window;
    }

    /** Number of readings currently in the window. */
    public int size() {
        return buffer.size();
    }
}
