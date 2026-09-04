package com.pollution.apiservice;

import com.pollution.apiservice.config.Config;
import com.pollution.apiservice.config.Wiring;
import com.pollution.apiservice.web.IWebServer;
import com.pollution.common.PollutionLogger;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionRepository;
import org.slf4j.Logger;

public class ApiServiceApplication {

    /* Runs before the logger field below triggers logback's configuration:
       static initializers execute in textual order (JLS 12.4.2), and
       Config.SERVICE_NAME is a compile-time constant, so reading it here
       does not initialize Config or anything Config touches. */
    static {
        PollutionLogger.initService(Config.SERVICE_NAME);
    }

    private static final Logger logger = PollutionLogger.getLogger(ApiServiceApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        IPollutionRepository repository = Wiring.createPollutionRepository();
        ILatestReadingStore latestReadings = Wiring.createLatestReadingStore();
        IWebServer server = Wiring.createWebServer(repository, latestReadings);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            latestReadings.close();
            repository.close();
        }, "api-shutdown"));
        server.start();
        logger.info("{} serving the dashboard at http://{}:{}/", Config.SERVICE_NAME, Config.getBindAddress(), Config.getPort());
    }
}
