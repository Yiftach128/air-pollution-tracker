package com.pollution.dataanalyzer.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * The complete state of one rolling average at a point in time: everything
 * needed to rebuild it after a restart.
 * <p>
 * Only a series' <em>longest</em> window is ever persisted; its readings are a
 * superset of every shorter window's, so those are rebuilt from this state by
 * dropping the readings outside their own window.
 *
 * @param sensorPollutant the series the readings belong to
 * @param window          how far back from {@code latest} the window reached when the state was captured
 * @param sum             running sum of the readings' values (informational: a restore recomputes it)
 * @param latest          newest timestamp ever seen by the average; {@code null} exactly when {@code readings} is empty
 * @param readings        the readings in the window, in queue order (oldest-arrived first)
 */
public record RollingAverageState(SensorPollutant sensorPollutant,
                                  Duration window,
                                  double sum,
                                  Instant latest,
                                  List<Reading> readings) {

    public RollingAverageState {
        Objects.requireNonNull(sensorPollutant, "sensorPollutant");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(readings, "readings");
        readings = List.copyOf(readings);
        if ((latest == null) != readings.isEmpty()) {
            throw new IllegalArgumentException(
                    "latest must be null exactly when readings is empty; latest=" + latest
                            + ", readings=" + readings.size());
        }
    }

    /** Number of readings in the window. */
    public int sampleCount() {
        return readings.size();
    }

    /** Mean of the readings in the window, or empty if there are none. */
    public OptionalDouble average() {
        return readings.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(sum / readings.size());
    }
}
