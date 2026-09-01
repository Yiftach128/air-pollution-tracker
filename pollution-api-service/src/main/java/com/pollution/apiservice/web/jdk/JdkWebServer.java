package com.pollution.apiservice.web.jdk;

import com.pollution.apiservice.web.IRequestHandler;
import com.pollution.apiservice.web.IWebServer;
import com.pollution.apiservice.web.Request;
import com.pollution.apiservice.web.Response;
import com.pollution.common.PollutionLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * An {@link IWebServer} on the HTTP server built into the JDK
 * ({@code jdk.httpserver}). This is the only class that sees an
 * {@link HttpExchange}: it turns each one into a {@link Request}, hands it
 * to the handler, and writes the {@link Response} back — with the cache
 * policy the response carries. Requests are answered on a small pool of
 * threads.
 */
public final class JdkWebServer implements IWebServer {

    private static final Logger logger = PollutionLogger.getLogger(JdkWebServer.class);

    private static final String THREAD_NAME_PREFIX = "api-http-";
    private static final int NO_BACKLOG_LIMIT = 0;
    private static final int STOP_DELAY_SECONDS = 0;

    private final HttpServer server;
    private final ExecutorService executor;
    private final IRequestHandler handler;

    /**
     * @param bindAddress the address to listen on
     * @param port        the port to listen on
     * @param threads     requests answered at once; positive
     * @param handler     answers every request
     * @throws UncheckedIOException if the address cannot be bound
     */
    public JdkWebServer(String bindAddress, int port, int threads, IRequestHandler handler) {
        Objects.requireNonNull(bindAddress, "bindAddress");
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, was " + threads);
        }
        this.handler = Objects.requireNonNull(handler, "handler");
        try {
            this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), NO_BACKLOG_LIMIT);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to bind http server to " + bindAddress + ":" + port, e);
        }
        AtomicInteger threadNumber = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(threads,
                runnable -> new Thread(runnable, THREAD_NAME_PREFIX + threadNumber.incrementAndGet()));
        server.setExecutor(executor);
        server.createContext("/", this::serve);
    }

    @Override
    public void start() {
        server.start();
        logger.info("http server listening on {}", server.getAddress());
    }

    @Override
    public void close() {
        server.stop(STOP_DELAY_SECONDS);
        executor.shutdownNow();
        logger.info("http server stopped");
    }

    private void serve(HttpExchange exchange) {
        try (exchange) {
            Request request = toRequest(exchange);
            Response response;
            try {
                response = handler.handle(request);
            } catch (RuntimeException e) {
                // the router answers everything a handler throws; this is a last resort
                logger.error("unhandled failure answering {} {}", request.method(), request.path(), e);
                response = Response.error(Response.INTERNAL_ERROR, "internal error");
            }
            write(exchange, response);
        } catch (IOException e) {
            logger.warn("failed to answer {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
        }
    }

    private static Request toRequest(HttpExchange exchange) {
        return new Request(exchange.getRequestMethod().toUpperCase(),
                exchange.getRequestURI().getPath(),
                parseQuery(exchange.getRequestURI().getRawQuery()));
    }

    /** Splits {@code a=1&b=2} into a map, decoding each part; a repeated parameter keeps its first value. */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String name = decode(separator < 0 ? pair : pair.substring(0, separator));
            String value = separator < 0 ? "" : decode(pair.substring(separator + 1));
            query.putIfAbsent(name, value);
        }
        return query;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static void write(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.getResponseHeaders().set("Cache-Control", response.cacheControl());
        byte[] body = response.body();
        // the JDK server takes -1 for an empty body; 0 would mean chunked encoding
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
