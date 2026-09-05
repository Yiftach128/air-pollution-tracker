package com.pollution.common.testing;

import com.pollution.common.pubsub.IPublisher;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link IPublisher} that keeps every message it is given instead of
 * sending it anywhere, so a test can assert what a service published and
 * under which key. Can be told to fail, to test what a service does when
 * its publisher is down.
 *
 * @param <T> the message type
 */
public final class RecordingPublisher<T> implements IPublisher<T> {

    /** One call to {@link #send}: the message and the key it was sent with. */
    public record Sent<T>(T message, String key) {
    }

    private final List<Sent<T>> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;
    private volatile boolean closed;

    @Override
    public void send(T message, String key) {
        Objects.requireNonNull(message, "message");
        RuntimeException currentFailure = failure;
        if (currentFailure != null) {
            throw currentFailure;
        }
        sent.add(new Sent<>(message, key));
    }

    /** Every successful send, in order. */
    public List<Sent<T>> sent() {
        return List.copyOf(sent);
    }

    /** The messages of every successful send, in order. */
    public List<T> messages() {
        return sent.stream().map(Sent::message).toList();
    }

    /** Makes every send throw {@code failure} until told otherwise ({@code null} to succeed again). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
