package com.pollution.alertservice.senders;

import com.pollution.common.entities.PollutionAlert;

/**
 * Delivers a {@link PollutionAlert} to the people who should hear about it,
 * over one channel: a chat bot, an e-mail list, a pager. One implementation
 * per channel, each in its own {@code senders/<channel>} sub-package; the
 * rest of the service only ever sees this interface.
 */
public interface IAlertSender extends AutoCloseable {

    /**
     * Delivers one alert. Returns once the channel has accepted the alert
     * (the API acknowledged it, the message was written), not once anyone
     * has read it.
     *
     * @throws AlertSendException if the alert could not be delivered; the
     *                            caller decides whether to retry or drop it
     */
    void send(PollutionAlert alert);

    /** Releases whatever the channel holds open (connections, clients). */
    @Override
    void close();
}
