package com.pollution.common.pubsub.kafka;

import com.pollution.common.json.JsonException;
import com.pollution.common.json.JsonSupport;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Kafka value serializer that writes any message type as JSON.
 */
public class JsonSerializer<T> implements Serializer<T> {

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        try {
            return JsonSupport.toJsonBytes(data);
        } catch (JsonException e) {
            throw new SerializationException("failed to serialize " + data.getClass().getSimpleName()
                    + " for topic " + topic, e);
        }
    }
}
