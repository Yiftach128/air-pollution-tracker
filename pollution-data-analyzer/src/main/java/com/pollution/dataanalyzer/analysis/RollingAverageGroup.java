package com.pollution.dataanalyzer.analysis;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.WindowAverage;
import com.pollution.dataanalyzer.entities.RollingAverageState;
import com.pollution.dataanalyzer.entities.SensorPollutant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * All the rolling averages of one {@link SensorPollutant} series, one per
 * window length, kept in ascending window order and fed the same readings.
 * <p>
 * Only the longest window is ever persisted and the shorter ones are derived
 * from it, which relies on three properties of {@link RollingAverage}:
 * <ul>
 *   <li>Every window sees the same readings and the same newest timestamp, and
 *       a longer window's cutoff is earlier, so the longest window's buffer is
 *       a superset of every shorter one's. Replaying it into a shorter window
 *       ({@link RollingAverage#loadFromPersistence}) reproduces that window
 *       exactly.</li>
 *   <li>For the same reason the longest window accepts a reading whenever any
 *       shorter one does, so its verdict is the group's "state changed" flag.</li>
 *   <li>The newest reading is never evicted, so whenever the group has a
 *       reading every window has at least one and every average is defined.</li>
 * </ul>
 * Not thread-safe: callers must serialize access, as with {@link RollingAverage}.
 */
public class RollingAverageGroup {

    private static final Logger logger = PollutionLogger.getLogger(RollingAverageGroup.class);

    private final SensorPollutant sensorPollutant;
    /** Ascending by window; the last one is the longest. */
    private final List<RollingAverage> averages;

    /**
     * Starts an empty group with one window per given length.
     *
     * @param windows the window lengths; validated and sorted by {@link #canonicalWindows}
     */
    public RollingAverageGroup(SensorPollutant sensorPollutant, List<Duration> windows) {
        this(sensorPollutant, windows, window -> new RollingAverage(sensorPollutant, window));
    }

    /** Builds one average per canonical window with {@code averageFor}. */
    private RollingAverageGroup(SensorPollutant sensorPollutant,
                                List<Duration> windows,
                                Function<Duration, RollingAverage> averageFor) {
        this.sensorPollutant = Objects.requireNonNull(sensorPollutant, "sensorPollutant");
        this.averages = canonicalWindows(windows).stream().map(averageFor).toList();
    }

    /**
     * Validates a set of window lengths — not empty, every one positive, no
     * duplicates — and returns them as an immutable ascending list.
     */
    public static List<Duration> canonicalWindows(List<Duration> windows) {
        Objects.requireNonNull(windows, "windows");
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("at least one window is required");
        }
        for (Duration window : windows) {
            Objects.requireNonNull(window, "window");
            if (window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("window must be positive, was " + window);
            }
        }
        List<Duration> sorted = windows.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1))) {
                throw new IllegalArgumentException("duplicate window " + sorted.get(i));
            }
        }
        return sorted;
    }

    /**
     * Rebuilds a group from the persisted state of its longest window: every
     * configured window is restored from that state, dropping the readings
     * outside its own reach.
     *
     * @param longestState state saved by {@link #getPersistentState()}
     * @param windows      the window lengths, as for the constructor
     */
    public static RollingAverageGroup loadFromPersistence(RollingAverageState longestState, List<Duration> windows) {
        Objects.requireNonNull(longestState, "longestState");
        RollingAverageGroup group = new RollingAverageGroup(longestState.sensorPollutant(), windows,
                window -> RollingAverage.loadFromPersistence(longestState, window));
        logger.info("{}: restored {} persisted readings into windows {}, latest {}",
                group.sensorPollutant, longestState.sampleCount(), group.describeSizes(), group.latest());
        return group;
    }

    /**
     * Feeds the reading to every window.
     *
     * @return whether any window accepted it, i.e. whether the group's state
     *         changed; this is the longest window's verdict
     */
    public boolean addReading(Instant timestamp, double value) {
        boolean changed = false;
        for (RollingAverage average : averages) {
            changed |= average.addReading(timestamp, value);
        }
        return changed;
    }

    /**
     * The state to persist: the longest window's, from which every shorter
     * window can be rebuilt with {@link #loadFromPersistence}.
     */
    public RollingAverageState getPersistentState() {
        return longest().getState();
    }

    /**
     * One average per window, ascending by window length; empty until the
     * first reading has been accepted, never empty afterwards.
     */
    public List<WindowAverage> averages() {
        List<WindowAverage> result = new ArrayList<>(averages.size());
        for (RollingAverage average : averages) {
            average.calculateAverage().ifPresent(
                    value -> result.add(new WindowAverage(average.window(), value, average.size())));
        }
        return List.copyOf(result);
    }

    /** Newest timestamp seen by the group, or {@code null} if no reading has been accepted yet. */
    public Instant latest() {
        return longest().latest();
    }

    /** The series whose readings are averaged. */
    public SensorPollutant sensorPollutant() {
        return sensorPollutant;
    }

    private RollingAverage longest() {
        return averages.get(averages.size() - 1);
    }

    private String describeSizes() {
        StringBuilder text = new StringBuilder("[");
        for (RollingAverage average : averages) {
            if (text.length() > 1) {
                text.append(", ");
            }
            text.append(average.window()).append('=').append(average.size());
        }
        return text.append(']').toString();
    }
}
