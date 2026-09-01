package com.pollution.apiservice;

import com.pollution.apiservice.config.Config;
import com.pollution.apiservice.config.Wiring;
import com.pollution.apiservice.web.IWebServer;
import com.pollution.common.PollutionLogger;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionRepository;
import org.slf4j.Logger;

public class ApiServiceApplication {

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
