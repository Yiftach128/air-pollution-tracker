package com.pollution.alertservice.senders;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import org.slf4j.Logger;

/**
 * A sender that delivers nothing: it logs the alert it would have sent.
 * Stands in for running without a real channel.
 */
public final class LoggingAlertSender implements IAlertSender {

    private static final Logger logger = PollutionLogger.getLogger(LoggingAlertSender.class);

    @Override
    public void send(PollutionAlert alert) {
        logger.info("would send {}", alert);
    }

    @Override
    public void close() {
    }
}
