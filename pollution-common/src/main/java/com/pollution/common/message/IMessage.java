package com.pollution.common.message;

import java.time.Instant;

/**
 * A message exchanged between services through the pubsub layer.
 * <p>
 * Implementations are immutable value types. Every message carries the city it
 * concerns, the instant it refers to, and a human-readable {@link #toString()}
 * suitable for logging.
 */
public interface IMessage {

    /**
     * The city this message is about.
     */
    String city();

    /**
     * The instant this message refers to: when a reading was taken, when an
     * averaging window closed, or when an alert was raised.
     */
    Instant timestamp();

    /**
     * Human-readable representation of the message, used in logs.
     */
    @Override
    String toString();
}
