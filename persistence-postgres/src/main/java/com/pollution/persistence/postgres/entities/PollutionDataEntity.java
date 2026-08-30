package com.pollution.persistence.postgres.entities;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * The row form of a {@link PollutionData} reading: what Hibernate maps to
 * the {@code pollution_data} table. Not a domain entity — it is mutable and
 * annotated because Hibernate needs it so — and it never leaves this module:
 * readings go in through {@link #fromDomain} and come out through
 * {@link #toDomain}.
 * <p>
 * A reading is unique by source, pollutant and time, so a redelivered
 * message cannot create a second row.
 */
@Entity
@Table(name = "pollution_data",
       uniqueConstraints = @UniqueConstraint(name = "uk_pollution_data_reading",
                                             columnNames = {"source", "pollutant", "measured_at"}))
public class PollutionDataEntity {

    /** Sequence-generated (not identity) so inserts can be batched later. */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String source;

    @Convert(converter = PollutantConverter.class)
    @Column(nullable = false, length = 16)
    private Pollutant pollutant;

    @Column(nullable = false)
    private double value;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    /** For Hibernate only. */
    protected PollutionDataEntity() {
    }

    private PollutionDataEntity(String city, String source, Pollutant pollutant, double value, Instant measuredAt) {
        this.city = city;
        this.source = source;
        this.pollutant = pollutant;
        this.value = value;
        this.measuredAt = measuredAt;
    }

    /** The row for a reading; its id is assigned when it is persisted. */
    public static PollutionDataEntity fromDomain(PollutionData reading) {
        return new PollutionDataEntity(
                reading.city(), reading.source(), reading.pollutant(), reading.value(), reading.timestamp());
    }

    /** The reading this row holds. */
    public PollutionData toDomain() {
        return new PollutionData(city, source, pollutant, value, measuredAt);
    }
}
