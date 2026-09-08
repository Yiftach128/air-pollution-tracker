package com.pollution.datacollector.registry;

import com.pollution.datacollector.entities.Shard;
import java.util.List;
import java.util.Optional;

/**
 * The sensors of one provider that this instance follows — its
 * {@link Shard}'s share of the configured ones — and their metadata,
 * fetched when a sensor is first followed and kept in memory for the life
 * of the service.
 *
 * @param <S> how the provider identifies a sensor we follow
 * @param <I> the metadata the provider reports for a sensor
 */
public interface ISensorRegistry<S, I> {

    /**
     * The sensors this instance follows now, in a stable order: its shard's
     * share of the configured ones. Empty until {@link #follow} is first called.
     */
    List<S> sensors();

    /**
     * Follows the shard's share of the configured sensors from now on. Each
     * sensor newly followed and not yet enriched is enriched; one whose
     * request fails is logged and followed un-enriched (and can be enriched
     * later with {@link #enrichSensor}). Does not throw for that.
     */
    void follow(Shard shard);

    /**
     * Fetches one sensor's metadata from the provider and stores it, replacing
     * any earlier entry.
     *
     * @throws RuntimeException the provider's failure type if the request failed; the registry is left unchanged
     */
    I enrichSensor(S sensor);

    /** The stored metadata, or empty if the sensor has not been enriched yet. */
    Optional<I> get(S sensor);
}
