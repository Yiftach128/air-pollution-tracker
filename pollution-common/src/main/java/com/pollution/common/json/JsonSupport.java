package com.pollution.common.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.io.IOException;

/**
 * The single JSON configuration shared by every service, so messages on the
 * wire and snapshots in persistence are encoded identically.
 * <p>
 * Values are plain immutable classes or records with no Jackson annotations,
 * so the mapper is told how to handle them here: every field is written (the
 * accessors are {@code city()}-style, not bean getters, so getter detection is
 * off), and an object is rebuilt by calling its public constructor with the
 * JSON properties matched to the constructor's parameter names. That needs the
 * names kept in the class files, which the build does with {@code -parameters}.
 * Fields are never written reflectively, so a value can only come into
 * existence through its constructor and the validation there.
 * <p>
 * Instants and durations are written as ISO-8601 strings, not numbers.
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new ParameterNamesModule())
            .visibility(PropertyAccessor.FIELD, Visibility.ANY)
            .visibility(PropertyAccessor.GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.SETTER, Visibility.NONE)
            .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonSupport() {
    }

    /** @throws JsonException if the value cannot be written */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new JsonException("failed to serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /** @throws JsonException if the value cannot be written */
    public static byte[] toJsonBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new JsonException("failed to serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /** @throws JsonException if the JSON is malformed or does not fit {@code type} */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new JsonException("failed to deserialize " + type.getSimpleName(), e);
        }
    }

    /** @throws JsonException if the JSON is malformed or does not fit {@code type} */
    public static <T> T fromJson(byte[] json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new JsonException("failed to deserialize " + type.getSimpleName(), e);
        }
    }
}
