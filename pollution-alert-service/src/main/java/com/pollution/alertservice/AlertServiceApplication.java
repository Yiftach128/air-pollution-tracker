package com.pollution.alertservice;

import com.pollution.alertservice.config.Config;
import com.pollution.alertservice.config.Wiring;
import com.pollution.alertservice.persistence.IAlertCooldownStore;
import com.pollution.alertservice.senders.IAlertSender;
import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.common.thresholds.Thresholds;
import org.slf4j.Logger;

public class AlertServiceApplication {

    /* Runs before the logger field below triggers logback's configuration:
       static initializers execute in textual order (JLS 12.4.2), and
       Config.SERVICE_NAME is a compile-time constant, so reading it here
       does not initialize Config or anything Config touches. */
    static {
        PollutionLogger.initService(Config.SERVICE_NAME);
    }

    private static final Logger logger = PollutionLogger.getLogger(AlertServiceApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
        ISubscriber<PollutionAverage> averageSubscriber = Wiring.createAverageSubscriber();
        IPublisher<PollutionAlert> alertPublisher = Wiring.createAlertPublisher();
        Thresholds thresholds = Config.getThresholds();
        IAlertCooldownStore cooldownStore = Wiring.createCooldownStore(thresholds);
        IAlertSender alertSender = Wiring.createAlertSender();
        PollutionAlertService service = Wiring.createAlertService(
                pollutionSubscriber, averageSubscriber, alertPublisher, cooldownStore, alertSender, thresholds);
        Runtime.getRuntime().addShutdownHook(new Thread(service::close, "alert-service-shutdown"));
        service.start();
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }
}
