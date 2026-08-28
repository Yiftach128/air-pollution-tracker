package com.pollution.alertservice.entities;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import java.time.Duration;
import java.util.Objects;

/**
 * The identity of an alert series: one pollutant at one source, measured one
 * way. Alerts in the same series are repeats of each other and share a
 * cooldown; alerts from a reading and from a rolling average of the same
 * pollutant are different series.
 *
 * @param source    identifier of the sensor/station
 * @param pollutant the pollutant measured
 * @param window    the rolling-average window, or {@code null} for single readings
 */
public record AlertSeries(String source, Pollutant pollutant, Duration window) {

    public AlertSeries {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
    }

    /** The series an alert belongs to. */
    public static AlertSeries of(PollutionAlert alert) {
        return new AlertSeries(alert.source(), alert.pollutant(), alert.window());
    }

    /**
     * True if an alert in this series makes one in {@code other} old news:
     * the same source and pollutant measured over a longer time — any window
     * supersedes a single reading, and a longer window a shorter one. A
     * series never supersedes itself.
     */
    public boolean supersedes(AlertSeries other) {
        if (window == null || !source.equals(other.source) || pollutant != other.pollutant) {
            return false;
        }
        return other.window == null || window.compareTo(other.window) > 0;
    }

    @Override
    public String toString() {
        return source + "/" + pollutant.displayName() + (window == null ? "" : "@" + window);
    }
}
