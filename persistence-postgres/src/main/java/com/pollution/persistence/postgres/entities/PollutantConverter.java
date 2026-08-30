package com.pollution.persistence.postgres.entities;

import com.pollution.common.entities.Pollutant;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link Pollutant} as its constant name in a plain {@code varchar}.
 * Used instead of {@code @Enumerated(EnumType.STRING)} because for that
 * Hibernate generates a {@code check (pollutant in (...))} constraint which
 * schema updates never widen, so adding a pollutant would break inserts.
 */
@Converter
public final class PollutantConverter implements AttributeConverter<Pollutant, String> {

    @Override
    public String convertToDatabaseColumn(Pollutant pollutant) {
        return pollutant == null ? null : pollutant.name();
    }

    @Override
    public Pollutant convertToEntityAttribute(String name) {
        return name == null ? null : Pollutant.valueOf(name);
    }
}
