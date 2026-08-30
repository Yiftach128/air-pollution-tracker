package com.pollution.persistence;

/** Thrown when an {@link IPollutionRepository} operation fails, e.g. the backing store is unreachable. */
public class PollutionRepositoryException extends RuntimeException {

    public PollutionRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
