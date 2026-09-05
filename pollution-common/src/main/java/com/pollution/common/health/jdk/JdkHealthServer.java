package com.pollution.common.health.jdk;

import com.pollution.common.PollutionLogger;
import com.pollution.common.health.IHealthServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

/**
 * An {@link IHealthServer} on the HTTP server built into the JDK
 * ({@code jdk.httpserver}). It listens on every interface, since probes
 * come from the platform to the pod's address, and answers nothing but the
 * two probe paths, in plain text, on one thread.
 */
public final class JdkHealthServer implements IHealthServer {

    private static final Logger logger = PollutionLogger.getLogger(JdkHealthServer.class);

    private static final String ALL_INTERFACES = "0.0.0.0";
    private static final String LIVENESS_PATH = "/healthz";
    private static final String READINESS_PATH = "/readyz";
    private static final String THREAD_NAME = "health-http";
    private static final int NO_BACKLOG_LIMIT = 0;
    private static final int STOP_DELAY_SECONDS = 0;
    private static final int MAX_PORT = 65535;
    /** What the JDK server takes as the response length of a HEAD request, which has no body. */
    private static final int NO_BODY = -1;

    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int SERVICE_UNAVAILABLE = 503;

    private final HttpServer server;
    private final ExecutorService executor;
    private volatile boolean ready;

    /**
     * @param port the port to listen on, on every interface
     * @throws UncheckedIOException if the port cannot be bound
     */
    public JdkHealthServer(int port) {
        if (port <= 0 || port > MAX_PORT) {
            throw new IllegalArgumentException("port must be between 1 and " + MAX_PORT + ", was " + port);
        }
        try {
            this.server = HttpServer.create(new InetSocketAddress(ALL_INTERFACES, port), NO_BACKLOG_LIMIT);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to bind health server to port " + port, e);
        }
        this.executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, THREAD_NAME));
        server.setExecutor(executor);
        server.createContext("/", this::serve);
    }

    @Override
    public void start() {
        server.start();
        logger.info("health server listening on {}: {} for liveness, {} for readiness",
                server.getAddress(), LIVENESS_PATH, READINESS_PATH);
    }

    @Override
    public void markReady() {
        ready = true;
        logger.info("ready");
    }

    @Override
    public void markNotReady() {
        ready = false;
        logger.info("no longer ready");
    }

    @Override
    public void close() {
        server.stop(STOP_DELAY_SECONDS);
        executor.shutdownNow();
        logger.info("health server stopped");
    }

    private void serve(HttpExchange exchange) {
        try (exchange) {
            String method = exchange.getRequestMethod().toUpperCase();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                write(exchange, METHOD_NOT_ALLOWED, "method not allowed");
                return;
            }
            switch (exchange.getRequestURI().getPath()) {
                case LIVENESS_PATH -> write(exchange, OK, "ok");
                case READINESS_PATH -> {
                    if (ready) {
                        write(exchange, OK, "ready");
                    } else {
                        write(exchange, SERVICE_UNAVAILABLE, "not ready");
                    }
                }
                default -> write(exchange, NOT_FOUND, "not found");
            }
        } catch (IOException e) {
            logger.warn("failed to answer {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
        }
    }

    private static void write(HttpExchange exchange, int status, String text) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.sendResponseHeaders(status, NO_BODY);
            return;
        }
        byte[] body = (text + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
