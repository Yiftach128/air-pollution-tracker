package com.pollution.datacollector.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.testing.MutableClock;
import com.pollution.datacollector.entities.Member;
import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.persistence.CacheBackedMembershipStore;
import com.pollution.datacollector.persistence.IMembershipStore;
import com.pollution.datacollector.testing.ManualScheduler;
import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.testing.InMemoryPollutionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * One instance's membership alone: its heartbeats are ticked by hand
 * against a store in memory, the other members are written into that store
 * as their own heartbeats would, and every shard the listener is told of is
 * recorded. Heartbeat every ten seconds, lease thirty.
 */
class LeaseGroupMembershipTest {

    private static final Instant T0 = Instant.parse("2026-09-07T10:00:00Z");
    private static final String PREFIX = "collector:member:";
    private static final Duration HEARTBEAT = Duration.ofSeconds(10);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Member SELF = new Member("m-self", T0);
    private static final String SELF_KEY = PREFIX + SELF.id();

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<Member> cache = new InMemoryPollutionCache<>(Member.class, clock);
    private final IMembershipStore store = new CacheBackedMembershipStore(cache, PREFIX);
    private final ManualScheduler scheduler = new ManualScheduler();
    private final List<Shard> shards = new ArrayList<>();
    private final IGroupMembership membership = new LeaseGroupMembership(store, SELF, HEARTBEAT, LEASE, scheduler, clock);

    /** Another instance registers, as its own heartbeat would. */
    private void otherMember(String id) {
        store.renew(new Member(id, T0), LEASE);
    }

    /** One heartbeat interval passes and the heartbeat runs. */
    private void heartbeat() {
        clock.advance(HEARTBEAT);
        scheduler.runAll();
    }

    @Test
    void startingSchedulesTheHeartbeatAtOnceAndThenEveryInterval() {
        membership.start(shards::add);

        assertEquals(List.of(new ManualScheduler.Scheduled(scheduler.tasks().get(0).task(), Duration.ZERO, HEARTBEAT)),
                scheduler.tasks());
    }

    @Test
    void theFirstHeartbeatRegistersAndTheSecondTakesTheShard() {
        membership.start(shards::add);

        scheduler.runAll();
        assertEquals(List.of(SELF_KEY), cache.keys());
        assertEquals(Optional.of(T0.plus(LEASE)), cache.expiryOf(SELF_KEY));
        assertEquals(List.of(), shards, "registered, but takes no shard until the others have had a heartbeat to see it");

        heartbeat();
        assertEquals(List.of(new Shard(0, 1)), shards);
    }

    @Test
    void theMembersRankByIdAcrossTheWholeGroup() {
        otherMember("m-a");
        otherMember("m-z");
        membership.start(shards::add);

        scheduler.runAll();
        heartbeat();

        assertEquals(List.of(new Shard(1, 3)), shards);
    }

    @Test
    void anUnchangedGroupIsNotReportedAgain() {
        membership.start(shards::add);
        scheduler.runAll();

        heartbeat();
        heartbeat();
        heartbeat();

        assertEquals(List.of(new Shard(0, 1)), shards);
    }

    @Test
    void aNewcomerIsSeenAtTheNextHeartbeat() {
        membership.start(shards::add);
        scheduler.runAll();
        heartbeat();

        otherMember("m-zed");
        heartbeat();

        assertEquals(List.of(new Shard(0, 1), new Shard(0, 2)), shards);
    }

    @Test
    void aMemberWhoseLeaseRanOutIsNoLongerCounted() {
        otherMember("m-a");
        membership.start(shards::add);
        scheduler.runAll();
        heartbeat();
        heartbeat();
        assertEquals(List.of(new Shard(1, 2)), shards);

        heartbeat(); // the other member's lease, taken at T0, has now run out

        assertEquals(List.of(new Shard(1, 2), new Shard(0, 1)), shards);
    }

    @Test
    void aMemberThatLeftIsNoLongerCountedEither() {
        otherMember("m-a");
        membership.start(shards::add);
        scheduler.runAll();
        heartbeat();

        store.leave(new Member("m-a", T0));
        heartbeat();

        assertEquals(List.of(new Shard(1, 2), new Shard(0, 1)), shards);
    }

    @Test
    void theLeaseIsRenewedEveryHeartbeat() {
        membership.start(shards::add);
        scheduler.runAll();

        heartbeat();
        heartbeat();

        assertEquals(Optional.of(T0.plus(HEARTBEAT.multipliedBy(2)).plus(LEASE)), cache.expiryOf(SELF_KEY));
    }

    @Test
    void aFailedHeartbeatWithinTheLeaseKeepsTheShard() {
        membership.start(shards::add);
        scheduler.runAll();
        heartbeat();
        cache.failWith(new PollutionCacheException("redis is down", null));

        heartbeat();
        heartbeat();

        assertEquals(List.of(new Shard(0, 1)), shards);
    }

    @Test
    void losingTheLeaseDropsTheShardAndRejoiningTakesItAgainAfterAHeartbeat() {
        membership.start(shards::add);
        scheduler.runAll();
        heartbeat(); // renewed at T0 + 10 s
        cache.failWith(new PollutionCacheException("redis is down", null));
        heartbeat();
        heartbeat();
        assertEquals(List.of(new Shard(0, 1)), shards, "the lease still holds");

        heartbeat(); // T0 + 40 s: a lease's worth without renewal
        assertEquals(List.of(new Shard(0, 1), Shard.NONE), shards, "the lease ran out: the others have the sensors now");

        cache.failWith(null);
        heartbeat();
        assertEquals(2, shards.size(), "back in the group, but a newcomer again: no shard until the next heartbeat");
        heartbeat();
        assertEquals(List.of(new Shard(0, 1), Shard.NONE, new Shard(0, 1)), shards);
    }

    @Test
    void whileTheStoreIsUnreachableTheInstanceCannotJoin() {
        cache.failWith(new PollutionCacheException("redis is down", null));
        membership.start(shards::add);

        scheduler.runAll();
        heartbeat();
        heartbeat();
        assertEquals(List.of(), shards);
        assertEquals(List.of(), cache.keys());

        cache.failWith(null);
        heartbeat();
        assertEquals(List.of(), shards, "registered now; the shard comes with the next heartbeat");
        heartbeat();
        assertEquals(List.of(new Shard(0, 1)), shards);
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheHeartbeats() {
        membership.start(shard -> {
            shards.add(shard);
            throw new RuntimeException("cannot reshard");
        });
        scheduler.runAll();
        heartbeat();

        otherMember("m-zed");
        heartbeat();

        assertEquals(List.of(new Shard(0, 1), new Shard(0, 2)), shards);
    }

    @Test
    void closingLeavesTheGroupAndClosesTheStore() {
        membership.start(shards::add);
        scheduler.runAll();

        membership.close();

        assertTrue(scheduler.isShutdown());
        assertEquals(List.of(), cache.keys(), "left rather than waiting for the lease to run out");
        assertTrue(cache.isClosed());
    }

    @Test
    void closingBeforeJoiningStillClosesTheStore() {
        membership.start(shards::add);

        membership.close();

        assertTrue(cache.isClosed());
    }

    @Test
    void theHeartbeatMustBePositiveAndTheLeaseAtLeastTwoOfThem() {
        assertThrows(IllegalArgumentException.class,
                () -> new LeaseGroupMembership(store, SELF, Duration.ZERO, LEASE, scheduler, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new LeaseGroupMembership(store, SELF, HEARTBEAT, HEARTBEAT.multipliedBy(2).minusSeconds(1), scheduler, clock));
        new LeaseGroupMembership(store, SELF, HEARTBEAT, HEARTBEAT.multipliedBy(2), scheduler, clock);
    }
}
