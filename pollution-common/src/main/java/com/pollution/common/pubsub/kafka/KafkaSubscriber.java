package com.pollution.common.pubsub.kafka;

import com.pollution.common.PollutionLogger;
import com.pollution.common.config.Config;
import com.pollution.common.pubsub.ISubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;

public class KafkaSubscriber<T> implements ISubscriber<T> {

    private static final Logger logger = PollutionLogger.getLogger(KafkaSubscriber.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final KafkaConsumer<String, T> consumer;
    private final String topic;
    private volatile boolean running;
    private Thread pollThread;

    public KafkaSubscriber(String topic, String groupId, Deserializer<T> valueDeserializer) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.consumer = new KafkaConsumer<>(props, new StringDeserializer(), valueDeserializer);
        this.topic = topic;
    }

    @Override
    public synchronized void subscribe(Consumer<T> messageHandler) {
        if (pollThread != null) {
            throw new IllegalStateException("already subscribed to topic " + topic);
        }
        running = true;
        pollThread = new Thread(() -> pollLoop(messageHandler), "kafka-subscriber-" + topic);
        pollThread.start();
    }

    private void pollLoop(Consumer<T> messageHandler) {
        try {
            consumer.subscribe(List.of(topic));
            while (running) {
                ConsumerRecords<String, T> records = consumer.poll(POLL_TIMEOUT);
                for (ConsumerRecord<String, T> record : records) {
                    handle(messageHandler, record);
                }
            }
        } catch (WakeupException e) {
            // expected when close() interrupts a blocked poll; rethrow if not shutting down
            if (running) {
                throw e;
            }
        } finally {
            consumer.close();
        }
    }

    private void handle(Consumer<T> messageHandler, ConsumerRecord<String, T> record) {
        try {
            messageHandler.accept(record.value());
        } catch (RuntimeException e) {
            logger.error("message handler failed for key {} at {}-{} offset {}",
                    record.key(), record.topic(), record.partition(), record.offset(), e);
        }
    }

    @Override
    public synchronized void close() {
        if (pollThread == null) {
            consumer.close();
            return;
        }
        running = false;
        consumer.wakeup();
        try {
            pollThread.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
