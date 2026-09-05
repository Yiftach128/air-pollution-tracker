package com.pollution.dataanalyzer.analysis;

import com.pollution.common.PollutionLogger;
import com.pollution.dataanalyzer.entities.Reading;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import org.slf4j.Logger;

/**
 * A time-windowed rolling average of one series' readings.
 * <p>
 * Readings are appended in arrival order; every {@link #addReading} evicts
 * readings older than {@code window}, measured back from the newest timestamp
 * seen so far, so the buffer only ever holds the current window. A running
 * sum keeps the average O(1) to compute.
 * <p>
 * A series' readings arrive in timestamp order — the collector steps every
 * republish of a reading forward, and the messaging keeps a series on one
 * partition — so a reading that is not newer than the newest one seen is a
 * repeat, not a late arrival: a redelivery after a crash or rebalance, or a
 * collector that restarted (or re-polled an unchanged sensor) and emits the
 * same timestamps again. Such a reading is ignored, so it is counted once
 * whatever the messaging's delivery guarantees, just as the writer's
 * repository stores it once.
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

    private final SensorPollutant sensorPollutant;
    private final Duration window;
    private final Deque<Reading> buffer = new ArrayDeque<>();
    private double sum;
    /** Newest timestamp seen so far; the window is measured back from it. Null until the first reading. */
    private Instant latest;

    /**
     * @param sensorPollutant the series whose readings are averaged
     * @param window          how far back from the newest reading the window reaches; must be positive
     */
    public RollingAverage(SensorPollutant sensorPollutant, Duration window) {
        this.sensorPollutant = Objects.requireNonNull(sensorPollutant, "sensorPollutant");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
    }

    /**
     * Rebuilds a rolling average from a persisted state.
     * <p>
     * The configured window wins over the persisted one: the state's readings
     * are replayed in order and any that fall outside
     * {@code latest - configuredWindow} are dropped, so the restored buffer and
     * sum are exactly what this window would have kept. This is how a shorter
     * window is derived from the persisted longest one; a window
     * <em>longer</em> than the persisted one starts short of readings and
     * only fills up as new ones arrive.
     *
     * @param state            state saved by {@link #getState()}
     * @param configuredWindow the window the restored instance should use
     */
    public static RollingAverage loadFromPersistence(RollingAverageState state, Duration configuredWindow) {
        Objects.requireNonNull(state, "state");
        RollingAverage restored = new RollingAverage(state.sensorPollutant(), configuredWindow);
        int comparison = configuredWindow.compareTo(state.window());
        if (comparison < 0) {
            logger.debug("{}: deriving {} window from persisted {} window", state.sensorPollutant(), configuredWindow, state.window());
        } else if (comparison > 0) {
            logger.warn("{}: configured window {} is longer than persisted {}; it holds only the persisted readings until it refills",
                    state.sensorPollutant(), configuredWindow, state.window());
        }
        restored.latest = state.latest();
        for (Reading reading : state.readings()) {
            // the persisted readings all precede the persisted latest: append, do not treat them as repeats
            restored.append(reading.timestamp(), reading.value());
        }
        logger.debug("{}: {} window restored {} of {} readings, latest {}",
                state.sensorPollutant(), configuredWindow, restored.size(), state.sampleCount(), restored.latest);
        return restored;
    }

    /**
     * Appends a reading to the window and drops every reading that has fallen
     * out of it, i.e. is older than {@code latest - window} where {@code latest}
     * is the newest timestamp seen so far. A reading that is not newer than
     * {@code latest} is a repeat (see the class comment) and is ignored, as is
     * one already older than the cutoff.
     *
     * @return whether the reading was accepted, i.e. whether the state changed
     */
    public boolean addReading(Instant timestamp, double value) {
        Objects.requireNonNull(timestamp, "timestamp");
        if (latest != null && !timestamp.isAfter(latest)) {
            logger.debug("{}: {} window ignoring repeated reading at {}, newest is {}", sensorPollutant, window, timestamp, latest);
            return false;
        }
        return append(timestamp, value);
    }

    /** {@link #addReading} without the repeat check; restoring replays persisted readings through it. */
    private boolean append(Instant timestamp, double value) {
        if (latest == null || timestamp.isAfter(latest)) {
            latest = timestamp;
        }
        Instant cutoff = latest.minus(window);
        if (timestamp.isBefore(cutoff)) {
            logger.debug("{}: {} window dropping late reading at {}, window starts at {}", sensorPollutant, window, timestamp, cutoff);
            return false;
        }

        buffer.add(new Reading(timestamp, value));
        sum += value;
        while (buffer.peek().timestamp().isBefore(cutoff)) {
            sum -= buffer.poll().value();
        }
        return true;
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
     * {@link #loadFromPersistence}. The returned state is immutable and
     * does not change when this average does.
     */
    public RollingAverageState getState() {
        return new RollingAverageState(sensorPollutant, window, sum, latest, List.copyOf(buffer));
    }

    /** The series whose readings are averaged. */
    public SensorPollutant sensorPollutant() {
        return sensorPollutant;
    }

    /** How far back from the newest reading the window reaches. */
    public Duration window() {
        return window;
    }

    /** Newest timestamp seen so far, or {@code null} if no reading has been accepted yet. */
    public Instant latest() {
        return latest;
    }

    /** Number of readings currently in the window. */
    public int size() {
        return buffer.size();
    }
}
