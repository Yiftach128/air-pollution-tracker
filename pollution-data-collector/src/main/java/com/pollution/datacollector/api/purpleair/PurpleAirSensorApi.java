package com.pollution.datacollector.api.purpleair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pollution.common.PollutionLogger;
import com.pollution.datacollector.api.IApiKeyProvider;
import com.pollution.datacollector.entities.purpleair.PurpleAirReading;
import com.pollution.datacollector.entities.purpleair.PurpleAirSensorInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Java face of the PurpleAir {@code GET /v1/sensors/{sensor_index}} endpoint.
 * <p>
 * Each request takes the next key from the {@link IApiKeyProvider}. If PurpleAir
 * rejects that key (bad key, out of points, rate limited), the request is retried
 * with the next key before giving up, so one exhausted account does not stop
 * collection while others still have points.
 */
public class PurpleAirSensorApi implements IPurpleAirSensorApi {

    private static final Logger logger = PollutionLogger.getLogger(PurpleAirSensorApi.class);

    private static final String READING_FIELDS = "pm2.5,last_seen";
    private static final String INFO_FIELDS = "name,latitude,longitude";
    private static final String API_KEY_HEADER = "X-API-Key";
    /** 401 bad key, 402 out of points, 403 key not permitted, 429 key rate-limited. */
    private static final Set<Integer> KEY_REJECTED_STATUSES = Set.of(401, 402, 403, 429);
    private static final int MAX_LOGGED_BODY_CHARS = 300;

    private final HttpClient http;
    private final URI baseUri;
    private final IApiKeyProvider apiKeys;
    private final Duration requestTimeout;
    private final ObjectMapper json = new ObjectMapper();

    public PurpleAirSensorApi(HttpClient http, URI baseUri, IApiKeyProvider apiKeys, Duration requestTimeout) {
        this.http = http;
        this.baseUri = baseUri;
        this.apiKeys = apiKeys;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Fetches the current PM2.5 reading of one sensor.
     *
     * @throws PurpleAirApiException if every key was rejected, or the request failed for another reason
     */
    @Override
    public PurpleAirReading fetchPm25(int sensorIndex) {
        return getSensor(sensorIndex, READING_FIELDS, sensor -> new PurpleAirReading(
                requireNumber(sensor, "pm2.5", sensorIndex).asDouble(),
                Instant.ofEpochSecond(requireNumber(sensor, "last_seen", sensorIndex).asLong())));
    }

    /**
     * Fetches one sensor's metadata (name and location).
     *
     * @throws PurpleAirApiException if every key was rejected, or the request failed for another reason
     */
    @Override
    public PurpleAirSensorInfo fetchSensorInfo(int sensorIndex) {
        return getSensor(sensorIndex, INFO_FIELDS, sensor -> new PurpleAirSensorInfo(
                sensorIndex,
                requireText(sensor, "name", sensorIndex),
                requireNumber(sensor, "latitude", sensorIndex).asDouble(),
                requireNumber(sensor, "longitude", sensorIndex).asDouble()));
    }

    /**
     * Requests {@code fields} of one sensor, rotating keys on rejection, and hands
     * the response's {@code sensor} object to {@code parser}.
     */
    private <T> T getSensor(int sensorIndex, String fields, Function<JsonNode, T> parser) {
        URI uri = baseUri.resolve("v1/sensors/" + sensorIndex + "?fields=" + fields);
        PurpleAirApiException lastRejection = null;
        for (int attempt = 0; attempt < apiKeys.size(); attempt++) {
            try {
                String body = request(uri, apiKeys.next(), sensorIndex);
                return parser.apply(sensorNode(body, sensorIndex));
            } catch (PurpleAirApiException e) {
                if (!KEY_REJECTED_STATUSES.contains(e.httpStatus())) {
                    throw e;
                }
                logger.warn("PurpleAir rejected an API key (HTTP {}) for sensor {}; trying the next key",
                        e.httpStatus(), sensorIndex);
                lastRejection = e;
            }
        }
        throw lastRejection;
    }

    private String request(URI uri, String apiKey, int sensorIndex) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header(API_KEY_HEADER, apiKey)
                .timeout(requestTimeout)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new PurpleAirApiException("request for sensor " + sensorIndex + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PurpleAirApiException("request for sensor " + sensorIndex + " interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new PurpleAirApiException("PurpleAir returned HTTP " + response.statusCode()
                    + " for sensor " + sensorIndex + ": " + abbreviate(response.body()), response.statusCode());
        }
        return response.body();
    }

    private JsonNode sensorNode(String body, int sensorIndex) {
        try {
            return json.readTree(body).path("sensor");
        } catch (IOException e) {
            throw new PurpleAirApiException("unparseable response for sensor " + sensorIndex, e);
        }
    }

    private static JsonNode requireNumber(JsonNode sensor, String field, int sensorIndex) {
        JsonNode value = sensor.path(field);
        if (!value.isNumber()) {
            throw new PurpleAirApiException("response for sensor " + sensorIndex
                    + " has no numeric '" + field + "': " + abbreviate(sensor.toString()), 200);
        }
        return value;
    }

    private static String requireText(JsonNode sensor, String field, int sensorIndex) {
        JsonNode value = sensor.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new PurpleAirApiException("response for sensor " + sensorIndex
                    + " has no '" + field + "': " + abbreviate(sensor.toString()), 200);
        }
        return value.asText();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= MAX_LOGGED_BODY_CHARS) {
            return oneLine;
        }
        return oneLine.substring(0, MAX_LOGGED_BODY_CHARS) + "...";
    }
}
