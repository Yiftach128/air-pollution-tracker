package com.pollution.datacollector.api.purpleair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.datacollector.api.RoundRobinApiKeyProvider;
import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The HTTP client against a stand-in for PurpleAir on a local port: what it
 * asks for, how it reads the answer, and how it rotates keys when one is
 * rejected. PurpleAir itself is not involved.
 */
class PurpleAirSensorApiTest {

    private record CannedResponse(int status, String body) {
    }

    private static final int SENSOR = 308702;
    private static final String READING_BODY = "{\"api_version\":\"V1.0\",\"sensor\":{\"sensor_index\":308702,\"last_seen\":1757066400,\"pm2.5\":12.5}}";
    private static final String INFO_BODY = "{\"sensor\":{\"sensor_index\":308702,\"name\":\"Ganei-Ayalon\",\"latitude\":31.9,\"longitude\":34.9}}";

    private final Queue<CannedResponse> responses = new ConcurrentLinkedQueue<>();
    private final List<String> keysSeen = new CopyOnWriteArrayList<>();
    private final List<String> requestsSeen = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            keysSeen.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            requestsSeen.add(exchange.getRequestURI().toString());
            CannedResponse response = responses.poll();
            if (response == null) {
                response = new CannedResponse(500, "no canned response left");
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private PurpleAirSensorApi api(String... keys) {
        return new PurpleAirSensorApi(HttpClient.newHttpClient(), baseUri,
                new RoundRobinApiKeyProvider(List.of(keys)), Duration.ofSeconds(5));
    }

    private void respond(int status, String body) {
        responses.add(new CannedResponse(status, body));
    }

    @Test
    void fetchesASensorsReadingAskingOnlyForTheFieldsItNeeds() {
        respond(200, READING_BODY);

        PurpleAirReading reading = api("k1").fetchPm25(SENSOR);

        assertEquals(new PurpleAirReading(12.5, Instant.ofEpochSecond(1757066400)), reading);
        assertEquals(List.of("/v1/sensors/308702?fields=pm2.5,last_seen"), requestsSeen);
        assertEquals(List.of("k1"), keysSeen);
    }

    @Test
    void fetchesASensorsMetadata() {
        respond(200, INFO_BODY);

        PurpleAirSensorInfo info = api("k1").fetchSensorInfo(SENSOR);

        assertEquals(new PurpleAirSensorInfo(SENSOR, "Ganei-Ayalon", 31.9, 34.9), info);
        assertEquals(List.of("/v1/sensors/308702?fields=name,latitude,longitude"), requestsSeen);
    }

    @Test
    void triesTheNextKeyWhenOneIsRejected() {
        respond(429, "{\"error\":\"RateLimitExceededError\"}");
        respond(200, READING_BODY);

        PurpleAirReading reading = api("k1", "k2").fetchPm25(SENSOR);

        assertEquals(12.5, reading.pm25());
        assertEquals(List.of("k1", "k2"), keysSeen);
    }

    @Test
    void givesUpWithTheLastRejectionOnceEveryKeyWasTried() {
        respond(401, "{\"error\":\"ApiKeyInvalidError\"}");
        respond(402, "{\"error\":\"OutOfPointsError\"}");

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> api("k1", "k2").fetchPm25(SENSOR));

        assertEquals(402, thrown.httpStatus());
        assertEquals(List.of("k1", "k2"), keysSeen);
    }

    @Test
    void doesNotRetryAFailureThatIsNotAboutTheKey() {
        respond(500, "internal error");

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> api("k1", "k2").fetchPm25(SENSOR));

        assertEquals(500, thrown.httpStatus());
        assertEquals(1, keysSeen.size());
    }

    @Test
    void aResponseWithoutTheFieldIsAnError() {
        respond(200, "{\"sensor\":{\"sensor_index\":308702,\"last_seen\":1757066400}}");

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> api("k1").fetchPm25(SENSOR));

        assertEquals(200, thrown.httpStatus());
        assertTrue(thrown.getMessage().contains("pm2.5"), thrown.getMessage());
    }

    @Test
    void aResponseWithABlankNameIsAnError() {
        respond(200, "{\"sensor\":{\"name\":\" \",\"latitude\":31.9,\"longitude\":34.9}}");

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> api("k1").fetchSensorInfo(SENSOR));

        assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
    }

    @Test
    void anUnparseableResponseIsAnError() {
        respond(200, "<html>maintenance</html>");

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> api("k1").fetchPm25(SENSOR));

        assertTrue(thrown.getMessage().contains("unparseable"), thrown.getMessage());
    }

    @Test
    void aConnectionFailureIsAnErrorWithNoStatus() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        PurpleAirSensorApi unreachable = new PurpleAirSensorApi(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + closedPort + "/"), new RoundRobinApiKeyProvider(List.of("k1")), Duration.ofSeconds(5));

        PurpleAirApiException thrown = assertThrows(PurpleAirApiException.class, () -> unreachable.fetchPm25(SENSOR));

        assertEquals(PurpleAirApiException.NO_RESPONSE, thrown.httpStatus());
        assertInstanceOf(IOException.class, thrown.getCause());
    }
}
