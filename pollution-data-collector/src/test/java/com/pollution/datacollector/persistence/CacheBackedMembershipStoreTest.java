package com.pollution.datacollector.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.testing.MutableClock;
import com.pollution.datacollector.entities.Member;
import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.testing.InMemoryPollutionCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheBackedMembershipStoreTest {

    private static final Instant T0 = Instant.parse("2026-09-07T10:00:00Z");
    private static final String PREFIX = "collector:member:";
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Member ALICE = new Member("alice", T0);
    private static final Member BOB = new Member("bob", T0.minusSeconds(60));

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<Member> cache = new InMemoryPollutionCache<>(Member.class, clock);
    private final IMembershipStore store = new CacheBackedMembershipStore(cache, PREFIX);

    @Test
    void nobodyIsAMemberUntilSomeoneRenews() {
        assertEquals(List.of(), store.members());
    }

    @Test
    void renewingRegistersTheMemberUnderItsIdForTheLease() {
        store.renew(ALICE, LEASE);

        assertEquals(List.of(PREFIX + "alice"), cache.keys());
        assertEquals(Optional.of(T0.plus(LEASE)), cache.expiryOf(PREFIX + "alice"));
        assertEquals(List.of(ALICE), store.members());
    }

    @Test
    void aRegistrationLapsesWhenItsLeaseRunsOut() {
        store.renew(ALICE, LEASE);

        clock.advance(LEASE.minusSeconds(1));
        assertEquals(List.of(ALICE), store.members());

        clock.advance(Duration.ofSeconds(1));
        assertEquals(List.of(), store.members());
    }

    @Test
    void renewingAgainRestartsTheLease() {
        store.renew(ALICE, LEASE);
        clock.advance(Duration.ofSeconds(20));

        store.renew(ALICE, LEASE);
        clock.advance(Duration.ofSeconds(20));

        assertEquals(List.of(ALICE), store.members());
        assertEquals(Optional.of(T0.plusSeconds(20).plus(LEASE)), cache.expiryOf(PREFIX + "alice"));
    }

    @Test
    void everyMemberStillLeasedIsListed() {
        store.renew(ALICE, LEASE);
        store.renew(BOB, LEASE);

        assertEquals(Set.of(ALICE, BOB), Set.copyOf(store.members()));
    }

    @Test
    void leavingEndsTheRegistrationAtOnce() {
        store.renew(ALICE, LEASE);

        store.leave(ALICE);

        assertEquals(List.of(), store.members());
        assertEquals(List.of(), cache.keys());
    }

    @Test
    void leavingWithoutARegistrationIsNothing() {
        store.leave(ALICE);

        assertEquals(List.of(), store.members());
    }

    @Test
    void aStoreThatCannotBeReachedThrowsOnEveryOperation() {
        cache.failWith(new PollutionCacheException("redis is down", null));

        assertThrows(PollutionCacheException.class, () -> store.renew(ALICE, LEASE));
        assertThrows(PollutionCacheException.class, store::members);
        assertThrows(PollutionCacheException.class, () -> store.leave(ALICE));
    }

    @Test
    void closingClosesTheCache() {
        store.close();

        assertTrue(cache.isClosed());
    }

    @Test
    void theKeyPrefixMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CacheBackedMembershipStore(cache, " "));
    }
}
