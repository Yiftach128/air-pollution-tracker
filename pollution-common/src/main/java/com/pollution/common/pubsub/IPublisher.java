package com.pollution.common.pubsub;

public interface IPublisher<T> extends AutoCloseable {

    void send(T message, String key);

    @Override
    void close();
}
