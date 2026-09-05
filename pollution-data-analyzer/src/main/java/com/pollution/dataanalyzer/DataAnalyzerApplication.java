package com.pollution.dataanalyzer;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.health.IHealthServer;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.dataanalyzer.config.Config;
import com.pollution.dataanalyzer.config.Wiring;
import com.pollution.dataanalyzer.persistence.IRollingAverageStateStore;
import org.slf4j.Logger;

public class DataAnalyzerApplication {

    /* Runs before the logger field below triggers logback's configuration:
       static initializers execute in textual order (JLS 12.4.2), and
       Config.SERVICE_NAME is a compile-time constant, so reading it here
       does not initialize Config or anything Config touches. */
    static {
        PollutionLogger.initService(Config.SERVICE_NAME);
    }

    private static final Logger logger = PollutionLogger.getLogger(DataAnalyzerApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        IHealthServer health = Wiring.createHealthServer();
        health.start();
        try {
            ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
            IPublisher<PollutionAverage> averagePublisher = Wiring.createAveragePublisher();
            IRollingAverageStateStore stateStore = Wiring.createStateStore();
            PollutionDataAnalyzerService service = Wiring.createAnalyzerService(pollutionSubscriber, averagePublisher, stateStore);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                health.markNotReady();
                service.close();
                health.close();
            }, "analyzer-shutdown"));
            service.start();
        } catch (RuntimeException e) {
            // a failed start must end the process, not leave it alive and never ready
            health.close();
            throw e;
        }
        health.markReady();
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }
}
