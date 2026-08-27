package com.pollution.common.json;

/**
 * Thrown by {@link JsonSupport} when a value cannot be written as JSON or
 * JSON cannot be read into the requested type. Unchecked, so callers that
 * treat a bad payload as a programming error need no boilerplate; callers
 * that expect bad input (e.g. a deserializer fed foreign records) catch it.
 */
public class JsonException extends RuntimeException {

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
