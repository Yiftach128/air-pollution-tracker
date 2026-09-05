package com.pollution.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequestAndResponseTest {

    @Test
    void aParameterIsTrimmedAndABlankOneIsAbsent() {
        Request request = new Request("GET", "/api/history", Map.of("source", " purpleair:x ", "from", "  "));

        assertEquals(Optional.of("purpleair:x"), request.param("source"));
        assertEquals(Optional.empty(), request.param("from"));
        assertEquals(Optional.empty(), request.param("to"));
    }

    @Test
    void aRequiredParameterThatIsMissingIsABadRequest() {
        Request request = new Request("GET", "/api/history", Map.of());

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> request.requiredParam("source"));

        assertEquals("missing parameter source", thrown.getMessage());
    }

    @Test
    void jsonAnswersAreOkUncacheableAndEncodedTheProjectsWay() {
        Response response = Response.json(List.of(Map.of("a", 1)));

        assertEquals(Response.OK, response.status());
        assertEquals(Response.JSON, response.contentType());
        assertEquals(Response.NO_CACHE, response.cacheControl());
        assertEquals("[{\"a\":1}]", response.bodyText());
    }

    @Test
    void anErrorIsAJsonObjectWithTheMessage() {
        Response response = Response.error(Response.BAD_REQUEST, "from is not before to");

        assertEquals(Response.BAD_REQUEST, response.status());
        assertEquals(Response.JSON, response.contentType());
        assertEquals("{\"error\":\"from is not before to\"}", response.bodyText());
        assertEquals("{\"error\":\"not found: /x\"}", Response.notFound("/x").bodyText());
    }

    @Test
    void readyMadeBodiesKeepTheirTypeAndAreUncacheableUnlessLongLived() {
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);

        Response plain = Response.bytes("text/css; charset=utf-8", body);
        Response pinned = Response.longLived("text/javascript; charset=utf-8", body);

        assertEquals("text/css; charset=utf-8", plain.contentType());
        assertEquals(Response.NO_CACHE, plain.cacheControl());
        assertEquals("public, max-age=604800, immutable", pinned.cacheControl());
        assertEquals("body", pinned.bodyText());
    }
}
