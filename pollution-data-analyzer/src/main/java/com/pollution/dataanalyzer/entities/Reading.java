package com.pollution.dataanalyzer.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * One timestamped value held in a sensor's rolling window.
 *
 * @param timestamp when the value was measured
 * @param value     the measured concentration
 */
public record Reading(Instant timestamp, double value) {

    public Reading {
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
