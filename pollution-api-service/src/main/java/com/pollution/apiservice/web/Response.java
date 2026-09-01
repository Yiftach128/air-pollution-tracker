package com.pollution.apiservice.web;

import com.pollution.common.json.JsonSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * What a handler answers: a status, a content type, the bytes of the body
 * and how long the answer may be cached. Built through the factories, so
 * every JSON body — data or error — is encoded the same way, and
 * everything is uncacheable unless it says otherwise.
 *
 * @param status       the HTTP status code
 * @param contentType  the body's media type
 * @param body         the body; empty for none
 * @param cacheControl the {@code Cache-Control} the answer is served with
 */
public record Response(int status, String contentType, byte[] body, String cacheControl) {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int INTERNAL_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;

    public static final String JSON = "application/json; charset=utf-8";

    /** The default: the dashboard always shows what the stores hold now. */
    public static final String NO_CACHE = "no-cache";

    /** For files that only ever change by changing name: kept for a week, never revalidated. */
    private static final String LONG_LIVED = "public, max-age=604800, immutable";

    public Response {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(cacheControl, "cacheControl");
    }

    /** {@code 200} with the value as JSON. */
    public static Response json(Object value) {
        return new Response(OK, JSON, JsonSupport.toJsonBytes(value), NO_CACHE);
    }

    /** {@code 200} with a ready-made body. */
    public static Response bytes(String contentType, byte[] body) {
        return new Response(OK, contentType, body, NO_CACHE);
    }

    /**
     * {@code 200} with a ready-made body the browser may keep for a week
     * without asking again. Only for content that never changes under its
     * name — changing it means renaming the file it came from.
     */
    public static Response longLived(String contentType, byte[] body) {
        return new Response(OK, contentType, body, LONG_LIVED);
    }

    /** The given status with a JSON body of the form {@code {"error": message}}. */
    public static Response error(int status, String message) {
        return new Response(status, JSON, JsonSupport.toJsonBytes(Map.of("error", message)), NO_CACHE);
    }

    public static Response notFound(String path) {
        return error(NOT_FOUND, "not found: " + path);
    }

    @Override
    public String toString() {
        return "Response{status=" + status + ", contentType=" + contentType
                + ", body=" + body.length + " bytes}";
    }

    /** The body as text, for logging and tests. */
    public String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
