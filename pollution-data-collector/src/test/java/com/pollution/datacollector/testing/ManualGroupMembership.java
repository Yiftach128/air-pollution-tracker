package com.pollution.datacollector.testing;

import com.pollution.datacollector.entities.Shard;
import com.pollution.datacollector.membership.IGroupMembership;
import com.pollution.datacollector.membership.IShardListener;
import java.util.Objects;

/**
 * An {@link IGroupMembership} with no group behind it: the test plays the
 * group and hands the instance its shard ({@link #assign}), on the test's
 * own thread. Remembers whether it was started and closed.
 */
public final class ManualGroupMembership implements IGroupMembership {

    private IShardListener listener;
    private boolean closed;

    /** The group gives this instance the shard; the listener is told at once. */
    public void assign(Shard shard) {
        if (listener == null) {
            throw new IllegalStateException("not started");
        }
        listener.shardChanged(shard);
    }

    public boolean isStarted() {
        return listener != null;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void start(IShardListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void close() {
        closed = true;
    }
}
