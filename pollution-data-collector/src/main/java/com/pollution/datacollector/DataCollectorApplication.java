package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.datacollector.config.Config;
import com.pollution.datacollector.config.Wiring;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import org.slf4j.Logger;

public class DataCollectorApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataCollectorApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        IReadingsFetcher readingsFetcher = Wiring.createReadingsFetcher();
        IPublisher<PollutionData> pollutionPublisher = Wiring.createPollutionPublisher();
        PollutionDataCollectorService service = Wiring.createCollectorService(readingsFetcher, pollutionPublisher);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.close();
            pollutionPublisher.close();
        }, "collector-shutdown"));
        service.start();
        logger.info("{} scheduled and running", Config.SERVICE_NAME);
    }
}
