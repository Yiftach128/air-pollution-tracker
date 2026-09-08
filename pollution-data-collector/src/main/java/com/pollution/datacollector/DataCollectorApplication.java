package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.health.IHealthServer;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.datacollector.config.Config;
import com.pollution.datacollector.config.Wiring;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import com.pollution.datacollector.membership.IGroupMembership;
import org.slf4j.Logger;

public class DataCollectorApplication {

    /* Runs before the logger field below triggers logback's configuration:
       static initializers execute in textual order (JLS 12.4.2), and
       Config.SERVICE_NAME is a compile-time constant, so reading it here
       does not initialize Config or anything Config touches. */
    static {
        PollutionLogger.initService(Config.SERVICE_NAME);
    }

    private static final Logger logger = PollutionLogger.getLogger(DataCollectorApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        IHealthServer health = Wiring.createHealthServer();
        health.start();
        try {
            IReadingsFetcher readingsFetcher = Wiring.createReadingsFetcher();
            IPublisher<PollutionData> pollutionPublisher = Wiring.createPollutionPublisher();
            IGroupMembership membership = Wiring.createGroupMembership();
            PollutionDataCollectorService service =
                    Wiring.createCollectorService(readingsFetcher, pollutionPublisher, membership);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                health.markNotReady();
                service.close(); // leaves the group, then stops the loops
                pollutionPublisher.close();
                health.close();
            }, "collector-shutdown"));
            service.start();
        } catch (RuntimeException e) {
            // a failed start must end the process, not leave it alive and never ready
            health.close();
            throw e;
        }
        health.markReady();
        logger.info("{} scheduled and running", Config.SERVICE_NAME);
    }
}
