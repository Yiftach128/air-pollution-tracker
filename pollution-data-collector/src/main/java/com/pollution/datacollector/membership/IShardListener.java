package com.pollution.datacollector.membership;

import com.pollution.datacollector.entities.Shard;

/**
 * Told each time this instance's {@link Shard} changes — on the membership's
 * own thread. An exception it throws is logged, and the membership goes on.
 */
@FunctionalInterface
public interface IShardListener {

    /** @param shard the shard this instance holds from now on; {@link Shard#NONE} when it holds none */
    void shardChanged(Shard shard);
}
