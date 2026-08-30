package com.pollution.datawriter;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionAlert;
import com.pollution.common.entities.PollutionAverage;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.ISubscriber;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionRepository;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Consumes the streams of readings, averages and alerts and stores them.
 * Today only {@link PollutionData} readings are stored: each goes to the
 * {@link IPollutionRepository} for keeps and to the
 * {@link ILatestReadingStore} as its series' current reading; averages and
 * alerts are only logged.
 * <p>
 * Messages arrive on the subscribers' threads. The two stores are updated
 * independently: a failure in one is logged and does not stop the other,
 * and neither is propagated — the next message is independent of it, the
 * repository is idempotent, so a redelivery stores the reading without
 * duplicating it, and the latest-reading store never goes backwards.
 */
public class PollutionDataWriterService implements AutoCloseable {

    private static final Logger logger = PollutionLogger.getLogger(PollutionDataWriterService.class);

    private final ISubscriber<PollutionData> pollutionSubscriber;
    private final ISubscriber<PollutionAverage> averageSubscriber;
    private final ISubscriber<PollutionAlert> alertSubscriber;
    private final IPollutionRepository repository;
    private final ILatestReadingStore latestReadings;

    public PollutionDataWriterService(ISubscriber<PollutionData> pollutionSubscriber,
                                      ISubscriber<PollutionAverage> averageSubscriber,
                                      ISubscriber<PollutionAlert> alertSubscriber,
                                      IPollutionRepository repository,
                                      ILatestReadingStore latestReadings) {
        this.pollutionSubscriber = Objects.requireNonNull(pollutionSubscriber, "pollutionSubscriber");
        this.averageSubscriber = Objects.requireNonNull(averageSubscriber, "averageSubscriber");
        this.alertSubscriber = Objects.requireNonNull(alertSubscriber, "alertSubscriber");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.latestReadings = Objects.requireNonNull(latestReadings, "latestReadings");
    }

    /** Subscribes to readings, averages and alerts. */
    public void start() {
        pollutionSubscriber.subscribe(this::handleReading);
        averageSubscriber.subscribe(this::handleAverage);
        alertSubscriber.subscribe(this::handleAlert);
        logger.info("subscribed to readings, averages and alerts");
    }

    private void handleReading(PollutionData reading) {
        logger.debug("received {}", reading);
        store(reading);
        makeCurrent(reading);
    }

    private void store(PollutionData reading) {
        try {
            if (repository.save(reading)) {
                logger.info("stored {}", reading);
            } else {
                logger.info("already stored {}", reading);
            }
        } catch (RuntimeException e) {
            logger.error("failed to store {}", reading, e);
        }
    }

    private void makeCurrent(PollutionData reading) {
        try {
            if (latestReadings.save(reading)) {
                logger.debug("now the current reading: {}", reading);
            } else {
                logger.info("kept a newer current reading over {}", reading);
            }
        } catch (RuntimeException e) {
            logger.error("failed to make current {}", reading, e);
        }
    }

    private void handleAverage(PollutionAverage average) {
        logger.info("received {} (not stored yet)", average);
    }

    private void handleAlert(PollutionAlert alert) {
        logger.info("received {} (not stored yet)", alert);
    }

    @Override
    public void close() {
        pollutionSubscriber.close();
        averageSubscriber.close();
        alertSubscriber.close();
        repository.close();
        latestReadings.close();
    }
}
