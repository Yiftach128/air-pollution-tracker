package com.pollution.apiservice.web;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An incoming HTTP request as the handlers see it: what was asked for,
 * stripped of everything about how it arrived. This is what keeps the
 * handlers independent of the server implementation.
 *
 * @param method the HTTP method, upper case ({@code GET})
 * @param path   the request path, without the query string ({@code /api/history})
 * @param query  the query parameters, decoded; a parameter given more than once keeps its first value
 */
public record Request(String method, String path, Map<String, String> query) {

    public Request {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        query = Map.copyOf(Objects.requireNonNull(query, "query"));
    }

    /** A query parameter's value, if it was given and is not blank. */
    public Optional<String> param(String name) {
        String value = query.get(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /**
     * A query parameter's value.
     *
     * @throws IllegalArgumentException if it was not given or is blank
     */
    public String requiredParam(String name) {
        return param(name).orElseThrow(() -> new IllegalArgumentException("missing parameter " + name));
    }
}
