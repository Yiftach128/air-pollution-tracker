package com.pollution.datacollector.api;

/**
 * Supplies the API key to use for the next request to an external API.
 */
public interface IApiKeyProvider {

    /**
     * The key to use for the next request. Repeated calls may return
     * different keys, e.g. to spread usage across several accounts.
     */
    String next();

    /**
     * How many distinct keys this provider cycles through; an upper bound on
     * how many times a caller should retry with a fresh key.
     */
    int size();
}
