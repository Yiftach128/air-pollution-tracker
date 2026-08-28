package com.pollution.alertservice.persistence;

import com.pollution.alertservice.entities.AlertSeries;
import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionAlert;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cooldown store that lives only as long as the process: the same rule as
 * the cache-backed one, kept in a map. Stands in for running without a cache;
 * after a restart every series may alert once more.
 */
public final class InMemoryAlertCooldownStore implements IAlertCooldownStore {

    private final AlertCooldowns cooldowns;
    private final Clock clock;
    private final Map<AlertSeries, Instant> sentAtBySeries = new ConcurrentHashMap<>();

    /**
     * @param cooldowns how long each kind of series stays suppressed after an alert
     * @param clock     the clock cooldowns are measured against
     */
    public InMemoryAlertCooldownStore(AlertCooldowns cooldowns, Clock clock) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Set<AlertSeries> coolingDownSeries(String source, Pollutant pollutant) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pollutant, "pollutant");
        Instant now = clock.instant();
        Set<AlertSeries> coolingDown = new HashSet<>();
        for (Map.Entry<AlertSeries, Instant> entry : sentAtBySeries.entrySet()) {
            AlertSeries series = entry.getKey();
            if (series.source().equals(source) && series.pollutant() == pollutant
                    && now.isBefore(entry.getValue().plus(cooldowns.cooldownOf(series)))) {
                coolingDown.add(series);
            }
        }
        return coolingDown;
    }

    @Override
    public void markSent(PollutionAlert alert) {
        sentAtBySeries.put(AlertSeries.of(alert), clock.instant());
    }

    @Override
    public void close() {
        sentAtBySeries.clear();
    }
}
