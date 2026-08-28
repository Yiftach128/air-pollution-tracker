package com.pollution.datawriter;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.datawriter.config.Config;
import com.pollution.datawriter.config.Wiring;
import org.slf4j.Logger;

public class DataWriterApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataWriterApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
        ISubscriber<PollutionAverage> averageSubscriber = Wiring.createAverageSubscriber();
        ISubscriber<PollutionAlert> alertSubscriber = Wiring.createAlertSubscriber();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pollutionSubscriber.close();
            averageSubscriber.close();
            alertSubscriber.close();
        }, "subscriber-shutdown"));
        pollutionSubscriber.subscribe(DataWriterApplication::handleMessage);
        averageSubscriber.subscribe(DataWriterApplication::handleMessage);
        alertSubscriber.subscribe(DataWriterApplication::handleMessage);
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }

    private static void handleMessage(PollutionData reading) {
        logger.info("received {}", reading);
    }

    private static void handleMessage(PollutionAverage average) {
        logger.info("received {}", average);
    }

    private static void handleMessage(PollutionAlert alert) {
        logger.info("received {}", alert);
    }
}
