package com.pollution.datacollector.registry;

import java.util.List;
import java.util.Optional;

/**
 * The followed sensors of one provider and their metadata, fetched once and
 * kept in memory for the life of the service.
 *
 * @param <S> how the provider identifies a sensor we follow
 * @param <I> the metadata the provider reports for a sensor
 */
public interface ISensorRegistry<S, I> {

    /** The sensors this registry covers, in a stable order. */
    List<S> sensors();

    /**
     * Fetches one sensor's metadata from the provider and stores it, replacing
     * any earlier entry.
     *
     * @throws RuntimeException the provider's failure type if the request failed; the registry is left unchanged
     */
    I enrichSensor(S sensor);

    /**
     * Enriches every sensor in {@link #sensors()}. A sensor whose request fails
     * is logged and left un-enriched; it does not stop the others.
     */
    void enrichAll();

    /** The stored metadata, or empty if the sensor has not been enriched yet. */
    Optional<I> get(S sensor);
}
