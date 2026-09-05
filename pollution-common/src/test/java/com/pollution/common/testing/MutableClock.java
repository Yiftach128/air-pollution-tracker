package com.pollution.common.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A {@link Clock} a test moves by hand, so anything that measures time — a
 * cooldown, a TTL, a reading's age — is tested without waiting for it.
 * Starts at the given instant and only moves when told to.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant now;

    private MutableClock(Instant start, ZoneId zone) {
        this.now = Objects.requireNonNull(start, "start");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** A clock stopped at {@code start}, in UTC. */
    public static MutableClock at(Instant start) {
        return new MutableClock(start, ZoneOffset.UTC);
    }

    /** Moves the clock forward by {@code amount}. */
    public void advance(Duration amount) {
        now = now.plus(Objects.requireNonNull(amount, "amount"));
    }

    /** Sets the clock to {@code instant}, forward or back. */
    public void set(Instant instant) {
        now = Objects.requireNonNull(instant, "instant");
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** A clock stopped at this one's current instant in another zone; it does not follow later moves of this one. */
    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public String toString() {
        return "MutableClock[" + now + "]";
    }
}
