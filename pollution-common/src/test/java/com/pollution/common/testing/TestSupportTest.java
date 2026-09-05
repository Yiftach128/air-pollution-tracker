package com.pollution.common.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The fakes every service's tests rely on must themselves do what they say. */
class TestSupportTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void theClockOnlyMovesWhenTold() {
        MutableClock clock = MutableClock.at(T0);

        assertEquals(T0, clock.instant());
        clock.advance(Duration.ofMinutes(5));
        assertEquals(T0.plus(Duration.ofMinutes(5)), clock.instant());
        clock.set(T0);
        assertEquals(T0, clock.instant());
    }

    @Test
    void thePublisherRecordsEverySendWithItsKey() {
        RecordingPublisher<String> publisher = new RecordingPublisher<>();

        publisher.send("one", "k1");
        publisher.send("two", "k2");

        assertEquals(List.of(new RecordingPublisher.Sent<>("one", "k1"), new RecordingPublisher.Sent<>("two", "k2")),
                publisher.sent());
        assertEquals(List.of("one", "two"), publisher.messages());
    }

    @Test
    void thePublisherFailsWhileToldToAndRecordsNothingMeanwhile() {
        RecordingPublisher<String> publisher = new RecordingPublisher<>();
        RuntimeException down = new RuntimeException("down");

        publisher.failWith(down);
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> publisher.send("lost", "k"));
        publisher.failWith(null);
        publisher.send("kept", "k");

        assertEquals(down, thrown);
        assertEquals(List.of("kept"), publisher.messages());
    }

    @Test
    void theSubscriberDeliversIntoTheSubscribedHandler() {
        ManualSubscriber<String> subscriber = new ManualSubscriber<>();
        AtomicReference<String> received = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> subscriber.deliver("too early"));
        subscriber.subscribe(received::set);
        subscriber.deliver("hello");

        assertTrue(subscriber.isSubscribed());
        assertEquals("hello", received.get());
        assertThrows(IllegalStateException.class, () -> subscriber.subscribe(received::set));
    }

    @Test
    void closingIsRemembered() {
        RecordingPublisher<String> publisher = new RecordingPublisher<>();
        ManualSubscriber<String> subscriber = new ManualSubscriber<>();

        assertFalse(publisher.isClosed());
        assertFalse(subscriber.isClosed());
        publisher.close();
        subscriber.close();
        assertTrue(publisher.isClosed());
        assertTrue(subscriber.isClosed());
    }
}
