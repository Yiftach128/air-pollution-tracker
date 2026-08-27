package com.pollution.common.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * A message exchanged between services through the pubsub layer.
 * <p>
 * Subclasses are immutable value types. Every message carries the city it
 * concerns, the instant it refers to, and a human-readable {@link #toString()}
 * suitable for logging.
 */
public abstract class AbstractMessage {

    private final String city;
    private final Instant timestamp;

    /**
     * @param city      the city this message is about
     * @param timestamp the instant this message refers to
     */
    protected AbstractMessage(String city, Instant timestamp) {
        this.city = Objects.requireNonNull(city, "city");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    /**
     * The city this message is about.
     */
    public final String city() {
        return city;
    }

    /**
     * The instant this message refers to: when a reading was taken, when an
     * averaging window closed, or when an alert was raised.
     */
    public final Instant timestamp() {
        return timestamp;
    }

    /**
     * Human-readable representation of the message, used in logs.
     */
    @Override
    public abstract String toString();

    /** Value equality: two messages are equal when all their fields are. */
    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();
}
