package com.pollution.alertservice.senders;

/**
 * An {@link IAlertSender} could not deliver an alert: transport error, the
 * channel rejected the message, or it is misconfigured.
 */
public class AlertSendException extends RuntimeException {

    public AlertSendException(String message) {
        super(message);
    }

    public AlertSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
