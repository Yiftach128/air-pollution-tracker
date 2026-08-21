package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.pubsub.Producer;
import com.pollution.datacollector.config.Config;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public class PollutionDataCollectorService implements AutoCloseable {

    private static final Logger logger = PollutionLogger.getLogger(PollutionDataCollectorService.class);

    private final Producer<String> pollutionProducer;
    private final ScheduledExecutorService scheduler;

    public PollutionDataCollectorService(Producer<String> pollutionProducer, ScheduledExecutorService scheduler) {
        this.pollutionProducer = pollutionProducer;
        this.scheduler = scheduler;
    }

    public void start() {
        long intervalMillis = Config.getPublishIntervalMillis();
        scheduler.scheduleAtFixedRate(this::safePublish, 0, intervalMillis, TimeUnit.MILLISECONDS);
        logger.info("publishing every {} ms", intervalMillis);
    }

    private void safePublish() {
        // an exception escaping a scheduled task silently cancels all future runs
        try {
            publishMessage(pollutionProducer);
        } catch (RuntimeException e) {
            logger.error("failed to publish message", e);
        }
    }

    private void publishMessage(Producer<String> pollutionProducer) {
        logger.info("publishing message");
        pollutionProducer.send("hello", Config.SERVICE_NAME);
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
