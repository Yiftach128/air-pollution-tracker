package com.pollution.apiservice.entities;

/**
 * The bounds the service puts on history requests, told to the page so it
 * can refuse an impossible range before asking (the service still enforces
 * them; this only saves the round trip).
 *
 * @param defaultHistoryHours how far back a history reaches when the request does not say, in hours
 * @param maxHistoryDays      the longest range one request may ask for, in days
 */
public record HistoryLimits(long defaultHistoryHours, long maxHistoryDays) {

    public HistoryLimits {
        if (defaultHistoryHours <= 0) {
            throw new IllegalArgumentException("defaultHistoryHours must be positive, was " + defaultHistoryHours);
        }
        if (maxHistoryDays <= 0) {
            throw new IllegalArgumentException("maxHistoryDays must be positive, was " + maxHistoryDays);
        }
    }
}
