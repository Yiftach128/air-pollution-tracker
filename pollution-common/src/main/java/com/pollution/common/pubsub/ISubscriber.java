package com.pollution.common.pubsub;

import java.util.function.Consumer;

public interface ISubscriber<T> extends AutoCloseable {

    void subscribe(Consumer<T> messageHandler);

    @Override
    void close();
}
