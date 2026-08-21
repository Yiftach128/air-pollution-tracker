package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.datacollector.config.Config;
import org.slf4j.Logger;

public class DataCollectorApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataCollectorApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        IPublisher<String> pollutionPublisher = Config.createPollutionPublisher();
        PollutionDataCollectorService service =
                new PollutionDataCollectorService(pollutionPublisher, Config.createScheduler());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.close();
            pollutionPublisher.close();
        }, "collector-shutdown"));
        service.start();
        logger.info("{} scheduled and running", Config.SERVICE_NAME);
    }
}
