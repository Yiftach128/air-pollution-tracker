package com.pollution.datacollector.membership;

/**
 * This instance's membership of the group of collectors that share the
 * sensors out between them. Once started it keeps the instance in the group
 * and works out the instance's {@link com.pollution.datacollector.entities.Shard}
 * from who else is in, telling the listener whenever that changes; closing
 * leaves the group, so the others take over at once rather than when the
 * lease lapses.
 */
public interface IGroupMembership extends AutoCloseable {

    /** Joins the group and keeps this instance in it; the listener hears of every change of its shard. */
    void start(IShardListener listener);

    /** Leaves the group and releases the membership's resources. */
    @Override
    void close();
}
