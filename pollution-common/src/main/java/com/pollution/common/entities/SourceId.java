package com.pollution.common.entities;

import java.util.Objects;
import java.util.Optional;

/**
 * The identity of a source of readings — {@link PollutionData#source()} —
 * taken apart: the provider the readings come from and the provider's own
 * name for the sensor. In messages, keys and tables a source is one string,
 * {@code <provider>:<sensor>} ({@code purpleair:Ganei-Ayalon}), so sensors
 * of different providers can never collide on name; this is the one place
 * that spells that form out. The provider cannot contain the separator; the
 * sensor may — the split is at the first one.
 *
 * @param provider who the readings come from ({@code purpleair}); not blank, no {@value #SEPARATOR}
 * @param sensor   the provider's name for the sensor; not blank
 */
public record SourceId(String provider, String sensor) {

    /** Separates the provider from the sensor in the string form. */
    public static final char SEPARATOR = ':';

    public SourceId {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(sensor, "sensor");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (provider.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException("provider must not contain '" + SEPARATOR + "', was " + provider);
        }
        if (sensor.isBlank()) {
            throw new IllegalArgumentException("sensor must not be blank");
        }
    }

    /**
     * The id of a source string, {@code <provider>:<sensor>}.
     *
     * @throws IllegalArgumentException if the string is not of that form
     */
    public static SourceId parse(String source) {
        Objects.requireNonNull(source, "source");
        int separator = source.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("source must be <provider>" + SEPARATOR + "<sensor>, was " + source);
        }
        return new SourceId(source.substring(0, separator), source.substring(separator + 1));
    }

    /** Like {@link #parse}, but empty for a string that is not of the form — for data that may predate it. */
    public static Optional<SourceId> tryParse(String source) {
        Objects.requireNonNull(source, "source");
        int separator = source.indexOf(SEPARATOR);
        if (separator <= 0 || separator == source.length() - 1
                || source.substring(0, separator).isBlank() || source.substring(separator + 1).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SourceId(source.substring(0, separator), source.substring(separator + 1)));
    }

    /** The source as it travels and is stored: {@code provider:sensor}. */
    @Override
    public String toString() {
        return provider + SEPARATOR + sensor;
    }
}
