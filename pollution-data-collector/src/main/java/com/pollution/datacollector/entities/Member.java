package com.pollution.datacollector.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * One collector instance as the group knows it: its id and when it joined.
 * The id is unique among the running instances and — ideally — the same
 * again when an instance restarts, so the restarted instance replaces its
 * old registration instead of standing beside it until the lease runs out
 * (hence the default of hostname and pid, see the collector's Config). The
 * members rank themselves by id; that is how each works out its
 * {@link Shard}. Stored as JSON, so: one public constructor whose parameter
 * names match the fields.
 *
 * @param id    the instance's id; not blank
 * @param since when the instance joined the group
 */
public record Member(String id, Instant since) {

    public Member {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(since, "since");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
