package com.pollution.datacollector;

import com.pollution.common.PollutionLogger;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.pubsub.IPublisher;
import com.pollution.datacollector.fetchers.IReadingsFetcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Runs two periodic tasks, each on its own thread so a slow poll never delays publishing:
 * <ul>
 *   <li><b>poll</b> — every {@link Schedule#pollInterval()}, fetches the latest
 *       reading of every sensor from the readings fetcher into an in-memory cache;</li>
 *   <li><b>publish</b> — every {@link Schedule#publishInterval()}, publishes every
 *       cached reading, so downstream sees a steady stream of readings between
 *       polls. The k-th publish of a reading is stamped {@code last_seen + k *
 *       publishInterval}: the first copy carries the sensor's own timestamp and
 *       each later copy steps forward by one publish interval.</li>
 * </ul>
 * A cached reading older than {@link Schedule#readingMaxAge()} (i.e. the source
 * has failed to refresh it) is dropped rather than replayed indefinitely.
 */
public class PollutionDataCollectorService implements AutoCloseable {

    private static final Logger logger = PollutionLogger.getLogger(PollutionDataCollectorService.class);

    /**
     * @param pollInterval    how often the readings fetcher is asked for fresh readings
     * @param publishInterval how often every cached reading is published
     * @param readingMaxAge   how long a reading may be republished without being refreshed
     */
    public record Schedule(Duration pollInterval, Duration publishInterval, Duration readingMaxAge) {
        public Schedule {
            requirePositive(pollInterval, "pollInterval");
            requirePositive(publishInterval, "publishInterval");
            requirePositive(readingMaxAge, "readingMaxAge");
            if (readingMaxAge.compareTo(pollInterval) < 0) {
                throw new IllegalArgumentException("readingMaxAge " + readingMaxAge
                        + " is shorter than pollInterval " + pollInterval + ": every reading would expire before refresh");
            }
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive, was " + duration);
            }
        }
    }

    private record CachedReading(PollutionData reading, Instant fetchedAt, int timesPublished) {
        CachedReading published() {
            return new CachedReading(reading, fetchedAt, timesPublished + 1);
        }
    }

    private final IReadingsFetcher readingsFetcher;
    private final IPublisher<PollutionData> pollutionPublisher;
    private final ScheduledExecutorService pollScheduler;
    private final ScheduledExecutorService publishScheduler;
    private final Schedule schedule;
    private final Clock clock;
    /** Last reading per sensor, keyed by {@link PollutionData#source()}. */
    private final ConcurrentMap<String, CachedReading> latestReadings = new ConcurrentHashMap<>();

    public PollutionDataCollectorService(IReadingsFetcher readingsFetcher,
                                         IPublisher<PollutionData> pollutionPublisher,
                                         ScheduledExecutorService pollScheduler,
                                         ScheduledExecutorService publishScheduler,
                                         Schedule schedule,
                                         Clock clock) {
        this.readingsFetcher = readingsFetcher;
        this.pollutionPublisher = pollutionPublisher;
        this.pollScheduler = pollScheduler;
        this.publishScheduler = publishScheduler;
        this.schedule = schedule;
        this.clock = clock;
    }

    public void start() {
        readingsFetcher.initialize();
        long pollMillis = schedule.pollInterval().toMillis();
        long publishMillis = schedule.publishInterval().toMillis();
        pollScheduler.scheduleAtFixedRate(guarded("poll", this::poll), 0, pollMillis, TimeUnit.MILLISECONDS);
        publishScheduler.scheduleAtFixedRate(guarded("publish", this::publish), publishMillis, publishMillis, TimeUnit.MILLISECONDS);
        logger.info("polling every {} ms, publishing every {} ms, dropping readings older than {} ms",
                pollMillis, publishMillis, schedule.readingMaxAge().toMillis());
    }

    /** An exception escaping a scheduled task silently cancels all its future runs. */
    private static Runnable guarded(String taskName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.error("{} task failed", taskName, e);
            }
        };
    }

    private void poll() {
        List<PollutionData> readings = readingsFetcher.fetch();
        Instant now = clock.instant();
        for (PollutionData reading : readings) {
            latestReadings.put(reading.source(), new CachedReading(reading, now, 0));
            logger.debug("cached {}", reading);
        }
        if (readings.isEmpty()) {
            logger.warn("poll returned no readings; cached readings will expire after {} ms", schedule.readingMaxAge().toMillis());
        } else {
            logger.info("cached {} readings", readings.size());
        }
    }

    private void publish() {
        Instant now = clock.instant();
        Instant oldestAllowed = now.minus(schedule.readingMaxAge());
        int published = 0;
        for (Map.Entry<String, CachedReading> entry : latestReadings.entrySet()) {
            CachedReading cached = entry.getValue();
            if (cached.fetchedAt().isBefore(oldestAllowed)) {
                latestReadings.remove(entry.getKey(), cached);
                logger.warn("dropping reading for {} fetched at {}: not refreshed for over {} ms",
                        entry.getKey(), cached.fetchedAt(), schedule.readingMaxAge().toMillis());
                continue;
            }
            PollutionData message = cached.reading().withTimestamp(republishTimestamp(cached));
            pollutionPublisher.send(message, message.source());
            // a concurrent poll may have replaced the entry; then its fresh copy starts from 0 as it should
            latestReadings.replace(entry.getKey(), cached, cached.published());
            logger.debug("published {}", message);
            published++;
        }
        if (published == 0) {
            logger.warn("nothing to publish");
        } else {
            logger.info("published {} readings", published);
        }
    }

    /** {@code last_seen} for the first publish, then one publish interval later per republish. */
    private Instant republishTimestamp(CachedReading cached) {
        return cached.reading().timestamp()
                .plus(schedule.publishInterval().multipliedBy(cached.timesPublished()));
    }

    @Override
    public void close() {
        shutdown(pollScheduler);
        shutdown(publishScheduler);
    }

    private static void shutdown(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
