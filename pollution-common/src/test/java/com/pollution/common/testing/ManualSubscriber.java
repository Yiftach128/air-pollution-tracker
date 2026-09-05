package com.pollution.common.testing;

import com.pollution.common.pubsub.ISubscriber;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An {@link ISubscriber} with no topic behind it: it keeps the handler a
 * service subscribes and lets the test {@link #deliver} messages into it,
 * on the test's own thread, so every assertion can follow the delivery
 * directly.
 *
 * @param <T> the message type
 */
public final class ManualSubscriber<T> implements ISubscriber<T> {

    private volatile Consumer<T> handler;
    private volatile boolean closed;

    @Override
    public void subscribe(Consumer<T> messageHandler) {
        Objects.requireNonNull(messageHandler, "messageHandler");
        if (handler != null) {
            throw new IllegalStateException("already subscribed");
        }
        handler = messageHandler;
    }

    /**
     * Hands the message to the subscribed handler, as the topic would.
     *
     * @throws IllegalStateException if nothing has subscribed yet
     */
    public void deliver(T message) {
        Consumer<T> current = handler;
        if (current == null) {
            throw new IllegalStateException("nothing has subscribed");
        }
        current.accept(message);
    }

    public boolean isSubscribed() {
        return handler != null;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
