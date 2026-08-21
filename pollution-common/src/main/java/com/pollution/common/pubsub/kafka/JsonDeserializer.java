package com.pollution.common.pubsub.kafka;

import com.pollution.common.PollutionLogger;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;

/**
 * Kafka value deserializer that reads JSON into the given message type.
 * <p>
 * A malformed record is logged and yields {@code null} rather than throwing:
 * throwing from a deserializer would wedge the consumer on the bad record
 * forever, since it can never be polled past.
 */
public class JsonDeserializer<T> implements Deserializer<T> {

    private static final Logger logger = PollutionLogger.getLogger(JsonDeserializer.class);

    private final Class<T> type;

    public JsonDeserializer(Class<T> type) {
        this.type = type;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return JsonSupport.MAPPER.readValue(data, type);
        } catch (IOException e) {
            logger.error("dropping undeserializable {} record from topic {}", type.getSimpleName(), topic, e);
            return null;
        }
    }
}
