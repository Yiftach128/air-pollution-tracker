package com.pollution.datacollector.api;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cycles through a fixed list of API keys, one per request, so the points
 * consumed are spread evenly across all of them.
 */
public class RoundRobinApiKeyProvider implements IApiKeyProvider {

    private final List<String> keys;
    private final AtomicInteger cursor = new AtomicInteger();

    public RoundRobinApiKeyProvider(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("at least one API key is required");
        }
        this.keys = List.copyOf(keys);
    }

    @Override
    public String next() {
        return keys.get(Math.floorMod(cursor.getAndIncrement(), keys.size()));
    }

    @Override
    public int size() {
        return keys.size();
    }
}
