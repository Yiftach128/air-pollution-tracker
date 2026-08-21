package com.pollution.common.pubsub.kafka;

import com.pollution.common.PollutionLogger;
import com.pollution.common.config.Config;
import com.pollution.common.pubsub.IPublisher;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

public class KafkaPublisher<T> implements IPublisher<T> {

    private static final Logger logger = PollutionLogger.getLogger(KafkaPublisher.class);

    private final org.apache.kafka.clients.producer.KafkaProducer<String, T> delegate;
    private final String topic;

    public KafkaPublisher(String topic, Serializer<T> valueSerializer) {
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
