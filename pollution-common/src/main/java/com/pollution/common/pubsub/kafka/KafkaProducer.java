package com.pollution.common.pubsub.kafka;

import com.pollution.common.PollutionLogger;
import com.pollution.common.config.Config;
import com.pollution.common.pubsub.Producer;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

public class KafkaProducer<T> implements Producer<T> {

    private static final Logger logger = PollutionLogger.getLogger(KafkaProducer.class);

    private final org.apache.kafka.clients.producer.KafkaProducer<String, T> delegate;
    private final String topic;

    public KafkaProducer(String topic, Serializer<T> valueSerializer) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.getKafkaBootstrapServers());
        this.delegate = new org.apache.kafka.clients.producer.KafkaProducer<>(props, new StringSerializer(), valueSerializer);
        this.topic = topic;
    }

    @Override
    public void send(T message, String key) {
        delegate.send(new ProducerRecord<>(topic, key, message), (metadata, exception) -> {
            if (exception != null) {
                logger.error("failed to send message with key {} to topic {}", key, topic, exception);
            } else {
                logger.debug("sent message with key {} to {}-{} offset {}",
                        key, metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    @Override
    public void close() {
        delegate.close();
    }
}
