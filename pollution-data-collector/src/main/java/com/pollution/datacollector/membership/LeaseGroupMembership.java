package com.pollution.datacollector.membership;

import com.pollution.common.PollutionLogger;
import com.pollution.datacollector.entities.Member;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.persistence.IMembershipStore;
import com.pollution.persistence.PollutionCacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Membership by lease. Every {@code heartbeatInterval} this instance renews
 * its registration in the {@link IMembershipStore} for {@code lease}, lists
 * the members whose leases are still running, ranks them by id and takes
 * the {@link Shard} of its own rank. Nothing coordinates the instances:
 * each sees the same registrations and ranks them the same way, so between
 * them they cover the sensors exactly once — within a heartbeat or so of
 * any change.
 * <ul>
 *   <li><b>Joining</b>: an instance's first heartbeat only registers it; it
 *       takes its shard from the second. By then every other member has had
 *       a heartbeat of its own, seen the newcomer and let go of the sensors
 *       that are now the newcomer's — so a sensor is never polled by two
 *       instances just because one joined.</li>
 *   <li><b>Leaving</b>: {@link #close()} removes the registration, and the
 *       others take the leaver's sensors at their next heartbeat. An
 *       instance that dies without leaving lapses when its lease runs out;
 *       that long, plus a heartbeat, its sensors go unpolled.</li>
 *   <li><b>Losing the lease</b>: an instance whose heartbeats fail keeps its
 *       shard while its last renewal is younger than the lease — the others
 *       still see it — and drops everything ({@link Shard#NONE}) once it is
 *       older, because by then the others have taken its sensors and
 *       polling on would double them. Better a short gap than two
 *       collectors on one sensor. When the store answers again it rejoins
 *       as a newcomer.</li>
 * </ul>
 * The lease must be at least two heartbeats, so that one failed heartbeat
 * never ends it.
 */
public final class LeaseGroupMembership implements IGroupMembership {

    private static final Logger logger = PollutionLogger.getLogger(LeaseGroupMembership.class);

    /** The least a lease may be, in heartbeats: one may fail without the lease lapsing. */
    static final int MIN_HEARTBEATS_PER_LEASE = 2;

    private final IMembershipStore store;
    private final Member self;
    private final Duration heartbeatInterval;
    private final Duration lease;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    // written on the scheduler's thread only; read by close() once that thread has stopped
    private volatile IShardListener listener;
    private volatile Instant lastRenewal;
    /** Heartbeats renewed since this instance last joined; 0 while it is not in the group. */
    private volatile int heartbeatsLeased;
    private volatile Shard current = Shard.NONE;

    /**
     * @param store             where the group registers
     * @param self              this instance
     * @param heartbeatInterval how often the lease is renewed and the group re-read; positive
     * @param lease             how long a registration lasts without renewal; at least two heartbeats
     * @param scheduler         runs the heartbeats; shut down by {@link #close()}
     * @param clock             what the lease is measured against
     */
    public LeaseGroupMembership(IMembershipStore store,
                                Member self,
                                Duration heartbeatInterval,
                                Duration lease,
                                ScheduledExecutorService scheduler,
                                Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.self = Objects.requireNonNull(self, "self");
        this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("heartbeatInterval must be positive, was " + heartbeatInterval);
        }
        if (lease.compareTo(heartbeatInterval.multipliedBy(MIN_HEARTBEATS_PER_LEASE)) < 0) {
            throw new IllegalArgumentException("lease " + lease + " must be at least " + MIN_HEARTBEATS_PER_LEASE
                    + " heartbeats of " + heartbeatInterval + ", so that one failed heartbeat does not end it");
        }
    }

    @Override
    public void start(IShardListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        scheduler.scheduleAtFixedRate(this::heartbeat, 0, heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        logger.info("joining the collector group as {}: heartbeat every {} ms, lease {} ms",
                self.id(), heartbeatInterval.toMillis(), lease.toMillis());
    }

    /** One heartbeat. Never throws: an exception escaping a scheduled task silently cancels all its future runs. */
    private void heartbeat() {
        try {
            if (!renew(clock.instant())) {
                return;
            }
            if (heartbeatsLeased < MIN_HEARTBEATS_PER_LEASE) {
                logger.info("registered as {}; taking a shard at the next heartbeat, once the others have seen it", self.id());
                return;
            }
            takeShard();
        } catch (RuntimeException e) {
            logger.error("heartbeat failed", e);
        }
    }

    /**
     * Renews the lease. Returns whether this instance is in the group with a
     * renewed lease; a failed renewal keeps the shard while the lease holds
     * and drops it once the lease has run out.
     */
    private boolean renew(Instant now) {
        try {
            store.renew(self, lease);
        } catch (PollutionCacheException e) {
            onRenewalFailed(now, e);
            return false;
        }
        if (heartbeatsLeased == 0) {
            logger.info("joined the collector group as {}", self.id());
        }
        heartbeatsLeased++;
        lastRenewal = now;
        return true;
    }

    private void onRenewalFailed(Instant now, PollutionCacheException e) {
        if (heartbeatsLeased == 0) {
            logger.warn("cannot join the collector group: {}", e.getMessage());
            return;
        }
        Duration sinceRenewal = Duration.between(lastRenewal, now);
        if (sinceRenewal.compareTo(lease) < 0) {
            logger.warn("heartbeat failed ({}); keeping shard {}, the lease holds for another {} ms",
                    e.getMessage(), current, lease.minus(sinceRenewal).toMillis());
            return;
        }
        logger.error("lease lost: no heartbeat renewed for {} ms, the lease being {} ms ({}); the others have taken"
                        + " the sensors, so dropping them until the store answers again",
                sinceRenewal.toMillis(), lease.toMillis(), e.getMessage());
        heartbeatsLeased = 0;
        apply(Shard.NONE, List.of());
    }

    /** Ranks the group by id and takes the shard of this instance's rank. */
    private void takeShard() {
        List<Member> members;
        try {
            members = store.members();
        } catch (PollutionCacheException e) {
            logger.warn("cannot list the collector group ({}); keeping shard {}", e.getMessage(), current);
            return;
        }
        TreeSet<String> ids = new TreeSet<>();
        ids.add(self.id()); // just renewed, so in the group whatever the listing says
        for (Member member : members) {
            ids.add(member.id());
        }
        List<String> ranked = List.copyOf(ids);
        apply(new Shard(ranked.indexOf(self.id()), ranked.size()), ranked);
    }

    private void apply(Shard shard, List<String> members) {
        if (shard.equals(current)) {
            return;
        }
        logger.info("shard {} of the collector group {}", shard, members);
        current = shard;
        try {
            listener.shardChanged(shard);
        } catch (RuntimeException e) {
            logger.error("the shard listener failed for shard {}", shard, e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            if (heartbeatsLeased > 0) {
                store.leave(self);
                logger.info("left the collector group");
            }
        } catch (PollutionCacheException e) {
            logger.warn("could not leave the collector group ({}); the lease lapses within {} ms",
                    e.getMessage(), lease.toMillis());
        } finally {
            store.close();
        }
    }
}
