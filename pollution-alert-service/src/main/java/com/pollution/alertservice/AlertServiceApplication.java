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
import org.slf4j.Logger;

public class AlertServiceApplication {

    private static final Logger logger = PollutionLogger.getLogger(AlertServiceApplication.class);

    public static void main(String[] args) {
        logger.info("{} starting", Config.SERVICE_NAME);
        ISubscriber<PollutionData> pollutionSubscriber = Wiring.createPollutionSubscriber();
        ISubscriber<PollutionAverage> averageSubscriber = Wiring.createAverageSubscriber();
        IPublisher<PollutionAlert> alertPublisher = Wiring.createAlertPublisher();
        IAlertCooldownStore cooldownStore = Wiring.createCooldownStore();
        IAlertSender alertSender = Wiring.createAlertSender();
        PollutionAlertService service = Wiring.createAlertService(
                pollutionSubscriber, averageSubscriber, alertPublisher, cooldownStore, alertSender);
        Runtime.getRuntime().addShutdownHook(new Thread(service::close, "alert-service-shutdown"));
        service.start();
        logger.info("{} subscribed and running", Config.SERVICE_NAME);
    }
}
