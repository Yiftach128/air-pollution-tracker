package com.pollution.datacollector.persistence;

import com.pollution.datacollector.entities.Member;
import java.time.Duration;
import java.util.List;

/**
 * Where the running collectors register themselves, so that each can see
 * who else is running. A registration is a lease: it lasts {@code lease}
 * from its last renewal and then lapses on its own, so an instance that
 * dies without leaving drops out of the group by itself.
 * <p>
 * Every method throws {@link com.pollution.persistence.PollutionCacheException}
 * if the store cannot be reached; the caller decides what that means for its lease.
 */
public interface IMembershipStore extends AutoCloseable {

    /** Registers the member, or renews its registration: it is a member for {@code lease} from now. */
    void renew(Member member, Duration lease);

    /** Every member whose lease is still running, in no particular order. */
    List<Member> members();

    /** Ends the member's registration now rather than when its lease runs out; nothing if it has none. */
    void leave(Member member);

    @Override
    void close();
}
