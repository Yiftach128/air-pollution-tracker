package com.pollution.datawriter;

import com.pollution.common.PollutionLogger;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.datawriter.config.Config;
import org.slf4j.Logger;

public class DataWriterApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataWriterApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<String> pollutionSubscriber = Config.createPollutionSubscriber();
        Runtime.getRuntime().addShutdownHook(
                new Thread(pollutionSubscriber::close, "subscriber-shutdown"));
        pollutionSubscriber.subscribe(DataWriterApplication::handleMessage);
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }

    private static void handleMessage(String message) {
        logger.info("received message: {}", message);
    }
}
