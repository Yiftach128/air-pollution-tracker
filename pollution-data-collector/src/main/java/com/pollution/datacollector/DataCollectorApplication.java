package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.pubsub.Producer;
import com.pollution.datacollector.config.Config;
import org.slf4j.Logger;

public class DataCollectorApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataCollectorApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        Producer<String> pollutionProducer = Config.createPollutionProducer();
        PollutionDataCollectorService service =
                new PollutionDataCollectorService(pollutionProducer, Config.createScheduler());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.close();
            pollutionProducer.close();
        }, "collector-shutdown"));
        service.start();
        logger.info("{} scheduled and running", Config.SERVICE_NAME);
    }
}
