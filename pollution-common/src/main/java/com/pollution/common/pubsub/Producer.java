package com.pollution.common.pubsub;

public interface Producer<T> extends AutoCloseable {

    void send(T message, String key);

    @Override
    void close();
}
