package com.pollution.alertservice.persistence;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import java.util.Set;

/**
 * Remembers, per {@link AlertSeries}, that an alert was recently raised, so
 * the same series — or a shorter measurement of the same source and
 * pollutant — is not alerted on again until that series' cooldown has
 * passed. Entries expire on their own; nothing has to be cleared.
 */
public interface IAlertCooldownStore extends AutoCloseable {

    /**
     * Every series of this source and pollutant that was marked sent less
     * than its cooldown ago; empty if none was.
     *
     * @throws RuntimeException if the store cannot be reached: callers decide
     *                          whether to alert anyway
     */
    Set<AlertSeries> coolingDownSeries(String source, Pollutant pollutant);

    /**
     * Records that this alert was just raised; its series is suppressed for
     * its cooldown from now, restarting any cooldown already running.
     */
    void markSent(PollutionAlert alert);

    @Override
    void close();
}
