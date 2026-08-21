package com.pollution.datacollector.api.purpleair;

/**
 * A PurpleAir request failed: transport error, non-2xx status, or a response
 * that did not contain the requested fields.
 */
public class PurpleAirApiException extends RuntimeException {

    /** Value of {@link #httpStatus()} when no HTTP response was received at all. */
    public static final int NO_RESPONSE = -1;

    private final int httpStatus;

    public PurpleAirApiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PurpleAirApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = NO_RESPONSE;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
