package com.pollution.datacollector.persistence;

import com.pollution.datacollector.entities.Member;
import com.pollution.persistence.IPollutionCache;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Keeps each member's registration in an {@link IPollutionCache} under the
 * key {@code <keyPrefix><id>} — e.g. {@code collector:member:pod-7-1} — with
 * a lifetime of its lease, so the cache itself ends a lapsed registration by
 * dropping the key: an instance is a member exactly while its key exists.
 * The group is found with the pattern {@code <keyPrefix>*}. Works with any
 * cache implementation; the concrete one is chosen in Wiring.
 */
public final class CacheBackedMembershipStore implements IMembershipStore {

    private final IPollutionCache<Member> cache;
    private final String keyPrefix;

    /**
     * @param cache     where the registrations live
     * @param keyPrefix the collector's key namespace for them; must not be blank
     */
    public CacheBackedMembershipStore(IPollutionCache<Member> cache, String keyPrefix) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
    }

    @Override
    public void renew(Member member, Duration lease) {
        cache.setObjectValue(keyOf(member), member, lease);
    }

    @Override
    public List<Member> members() {
        return cache.getObjectValuesByPattern(keyPrefix + "*");
    }

    @Override
    public void leave(Member member) {
        cache.removeObject(keyOf(member));
    }

    @Override
    public void close() {
        cache.close();
    }

    private String keyOf(Member member) {
        return keyPrefix + Objects.requireNonNull(member, "member").id();
    }
}
