package com.pollution.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pollution.persistence.PollutionCacheException;
import com.pollution.persistence.PollutionRepositoryException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static Request get(String path) {
        return new Request("GET", path, Map.of());
    }

    private static IRequestHandler throwing(RuntimeException failure) {
        return request -> {
            throw failure;
        };
    }

    @Test
    void routesAnExactPathToItsHandler() {
        Router router = new Router().route("/api/ping", request -> Response.json(Map.of("pong", true)));

        Response response = router.handle(get("/api/ping"));

        assertEquals(Response.OK, response.status());
        assertEquals(Response.JSON, response.contentType());
        assertEquals("{\"pong\":true}", response.bodyText());
    }

    @Test
    void anUnknownPathIsNotFoundUnlessAFallbackIsGiven() {
        Router router = new Router().route("/api/ping", request -> Response.json(Map.of()));

        Response response = router.handle(get("/api/pong"));

        assertEquals(Response.NOT_FOUND, response.status());
        assertEquals("{\"error\":\"not found: /api/pong\"}", response.bodyText());
    }

    @Test
    void theFallbackServesEveryPathWithoutAHandler() {
        Router router = new Router()
                .route("/api/ping", request -> Response.json(Map.of()))
                .fallback(request -> Response.bytes("text/plain", request.path().getBytes()));

        assertEquals("/anything", router.handle(get("/anything")).bodyText());
        assertEquals("{}", router.handle(get("/api/ping")).bodyText());
    }

    @Test
    void onlyGetIsServed() {
        Router router = new Router().route("/api/ping", request -> Response.json(Map.of()));

        Response response = router.handle(new Request("POST", "/api/ping", Map.of()));

        assertEquals(Response.METHOD_NOT_ALLOWED, response.status());
    }

    @Test
    void aBadParameterIsTheClientsError() {
        Router router = new Router().route("/api/x", throwing(new IllegalArgumentException("missing parameter source")));

        Response response = router.handle(get("/api/x"));

        assertEquals(Response.BAD_REQUEST, response.status());
        assertEquals("{\"error\":\"missing parameter source\"}", response.bodyText());
    }

    @Test
    void aStoreThatCannotBeReachedMakesTheServiceUnavailable() {
        Router repositoryDown = new Router().route("/api/x", throwing(new PollutionRepositoryException("postgres is down", null)));
        Router cacheDown = new Router().route("/api/x", throwing(new PollutionCacheException("redis is down", null)));

        assertEquals(Response.SERVICE_UNAVAILABLE, repositoryDown.handle(get("/api/x")).status());
        assertEquals(Response.SERVICE_UNAVAILABLE, cacheDown.handle(get("/api/x")).status());
    }

    @Test
    void anythingElseIsAnInternalErrorThatSaysNothingMore() {
        Router router = new Router().route("/api/x", throwing(new IllegalStateException("table pollution_data is corrupt")));

        Response response = router.handle(get("/api/x"));

        assertEquals(Response.INTERNAL_ERROR, response.status());
        assertEquals("{\"error\":\"internal error\"}", response.bodyText());
    }

    @Test
    void everyAnswerIsUncacheableUnlessTheHandlerSaysOtherwise() {
        Router router = new Router()
                .route("/api/x", request -> Response.json(Map.of()))
                .route("/lib", request -> Response.longLived("text/javascript", new byte[0]));

        assertEquals(Response.NO_CACHE, router.handle(get("/api/x")).cacheControl());
        assertEquals(Response.NO_CACHE, router.handle(get("/missing")).cacheControl());
        assertEquals("public, max-age=604800, immutable", router.handle(get("/lib")).cacheControl());
    }
}
