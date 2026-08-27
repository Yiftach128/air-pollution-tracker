package com.pollution.persistence;

/** Thrown when an {@link IPollutionCache} operation fails, e.g. the backing store is unreachable. */
public class PollutionCacheException extends RuntimeException {

    public PollutionCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
