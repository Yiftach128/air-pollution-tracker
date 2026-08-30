package com.pollution.datawriter;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.datawriter.config.Config;
import com.pollution.datawriter.config.Wiring;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionRepository;
import org.slf4j.Logger;

public class DataWriterApplication {

    private static final Logger logger = PollutionLogger.getLogger(DataWriterApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
        ISubscriber<PollutionAverage> averageSubscriber = Wiring.createAverageSubscriber();
        ISubscriber<PollutionAlert> alertSubscriber = Wiring.createAlertSubscriber();
        IPollutionRepository repository = Wiring.createPollutionRepository();
        ILatestReadingStore latestReadings = Wiring.createLatestReadingStore();
        PollutionDataWriterService service = Wiring.createWriterService(
                pollutionSubscriber, averageSubscriber, alertSubscriber, repository, latestReadings);
        Runtime.getRuntime().addShutdownHook(new Thread(service::close, "writer-shutdown"));
        service.start();
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }
}
