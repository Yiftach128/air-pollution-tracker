package com.pollution.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The dashboard's files as served from the module's own {@code static/} directory. */
class StaticResourceHandlerTest {

    private final StaticResourceHandler handler = new StaticResourceHandler("static");

    private static Request get(String path) {
        return new Request("GET", path, Map.of());
    }

    @Test
    void theRootIsTheOverviewPage() {
        Response response = handler.handle(get("/"));

        assertEquals(Response.OK, response.status());
        assertEquals("text/html; charset=utf-8", response.contentType());
        assertTrue(response.bodyText().toLowerCase().contains("<html"));
        assertEquals(Response.NO_CACHE, response.cacheControl());
    }

    @Test
    void theHistoryPathIsTheHistoryPage() {
        Response response = handler.handle(get("/history"));

        assertEquals(Response.OK, response.status());
        assertEquals("text/html; charset=utf-8", response.contentType());
        assertTrue(response.bodyText().contains("history.js"));
    }

    @Test
    void anyOtherPathIsAFileOfThatNameServedByItsType() {
        assertEquals("text/css; charset=utf-8", handler.handle(get("/style.css")).contentType());
        assertEquals("text/javascript; charset=utf-8", handler.handle(get("/common.js")).contentType());
        assertEquals("image/svg+xml", handler.handle(get("/favicon.svg")).contentType());
    }

    @Test
    void vendoredLibrariesAreServedLongLived() {
        Response response = handler.handle(get("/vendor/chart.umd.js"));

        assertEquals(Response.OK, response.status());
        assertEquals("public, max-age=604800, immutable", response.cacheControl());
    }

    @Test
    void aMissingFileIsNotFound() {
        assertEquals(Response.NOT_FOUND, handler.handle(get("/missing.js")).status());
    }

    @Test
    void aFileOfAnUnknownTypeIsNotFound() {
        assertEquals(Response.NOT_FOUND, handler.handle(get("/README.md")).status());
        assertEquals(Response.NOT_FOUND, handler.handle(get("/noextension")).status());
    }

    @Test
    void nothingOutsideTheDirectoryCanBeReached() {
        assertEquals(Response.NOT_FOUND, handler.handle(get("/../pom.xml")).status());
        assertEquals(Response.NOT_FOUND, handler.handle(get("/static/../../pom.xml")).status());
        assertEquals(Response.NOT_FOUND, handler.handle(get("//style.css")).status());
        assertEquals(Response.NOT_FOUND, handler.handle(get("/..")).status());
    }
}
