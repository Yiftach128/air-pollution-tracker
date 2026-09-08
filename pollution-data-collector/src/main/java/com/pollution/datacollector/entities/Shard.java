package com.pollution.datacollector.entities;

import java.util.List;
import java.util.stream.IntStream;

/**
 * One instance's share of what a group shares out: the member of rank
 * {@code rank} among {@code count} takes the items at positions
 * {@code rank, rank + count, rank + 2 count, ...} of the list — of the
 * configured sensors, the ones it polls. Every member ranks the group the
 * same way (by id), so between them they cover the list exactly once
 * without any being told what the others do. Positions rather than ids,
 * so the shares differ in size by at most one.
 * <p>
 * {@link #NONE} is the share of an instance that is not in the group — one
 * that has not joined yet, or whose lease ran out: nothing.
 *
 * @param rank  this instance's position among the members, {@code 0 <= rank < count}
 * @param count how many members the group has; {@code 0} only for {@link #NONE}
 */
public record Shard(int rank, int count) {

    /** The share of an instance that is not in the group: nothing. */
    public static final Shard NONE = new Shard(0, 0);

    public Shard {
        if (count < 0 || rank < 0 || (count == 0 ? rank != 0 : rank >= count)) {
            throw new IllegalArgumentException("rank must be in [0, count), was rank " + rank + " of " + count);
        }
    }

    /** Whether this is {@link #NONE}: no share at all. */
    public boolean isNone() {
        return count == 0;
    }

    /**
     * This shard's share of the list: the items at the positions congruent
     * to {@code rank} modulo {@code count}, in the list's order.
     */
    public <T> List<T> select(List<T> all) {
        if (isNone()) {
            return List.of();
        }
        return IntStream.range(0, all.size())
                .filter(position -> position % count == rank)
                .mapToObj(all::get)
                .toList();
    }

    @Override
    public String toString() {
        return isNone() ? "none" : rank + " of " + count;
    }
}
