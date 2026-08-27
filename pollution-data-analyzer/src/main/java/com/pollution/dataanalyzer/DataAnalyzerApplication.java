package com.pollution.dataanalyzer;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.dataanalyzer.config.Config;
import com.pollution.dataanalyzer.config.Wiring;
import com.pollution.dataanalyzer.persistence.IRollingAverageStateStore;
import org.slf4j.Logger;

public class DataAnalyzerApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataAnalyzerApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
        IPublisher<PollutionAverage> averagePublisher = Wiring.createAveragePublisher();
        IRollingAverageStateStore stateStore = Wiring.createStateStore();
        PollutionDataAnalyzerService service = Wiring.createAnalyzerService(pollutionSubscriber, averagePublisher, stateStore);
        Runtime.getRuntime().addShutdownHook(new Thread(service::close, "analyzer-shutdown"));
        service.start();
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }
}
