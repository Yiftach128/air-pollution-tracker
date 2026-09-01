package com.pollution.apiservice.web;

import com.pollution.common.PollutionLogger;
import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.PollutionRepositoryException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Hands each request to the handler registered for its path — or to the
 * fallback handler when none is — and turns what a handler throws into the
 * answer the client should see: a bad parameter is the client's error, a
 * store that cannot be reached is the service being unavailable, anything
 * else is a bug. Only {@code GET} is served; the API is read-only.
 */
public final class Router implements IRequestHandler {

    private static final Logger logger = PollutionLogger.getLogger(Router.class);

    private static final String GET = "GET";

    private final Map<String, IRequestHandler> handlersByPath = new HashMap<>();
    private IRequestHandler fallback = request -> Response.notFound(request.path());

    /** Serves the exact path with the handler. */
    public Router route(String path, IRequestHandler handler) {
        handlersByPath.put(Objects.requireNonNull(path, "path"), Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Serves every path without a handler of its own with this one; by default they are not found. */
    public Router fallback(IRequestHandler handler) {
        fallback = Objects.requireNonNull(handler, "handler");
        return this;
    }

    @Override
    public Response handle(Request request) {
        if (!GET.equals(request.method())) {
            return Response.error(Response.METHOD_NOT_ALLOWED, "only GET is served");
        }
        IRequestHandler handler = handlersByPath.getOrDefault(request.path(), fallback);
        try {
            return handler.handle(request);
        } catch (IllegalArgumentException e) {
            logger.debug("rejected {} {}: {}", request.method(), request.path(), e.getMessage());
            return Response.error(Response.BAD_REQUEST, e.getMessage());
        } catch (PollutionRepositoryException | PollutionCacheException e) {
            logger.error("a store failed answering {} {}", request.method(), request.path(), e);
            return Response.error(Response.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (RuntimeException e) {
            logger.error("failed answering {} {}", request.method(), request.path(), e);
            return Response.error(Response.INTERNAL_ERROR, "internal error");
        }
    }
}
