package com.pollution.common.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceIdTest {

    @Test
    void parsesProviderAndSensor() {
        SourceId id = SourceId.parse("purpleair:Ganei-Ayalon");

        assertEquals("purpleair", id.provider());
        assertEquals("Ganei-Ayalon", id.sensor());
    }

    @Test
    void splitsAtTheFirstSeparatorSoASensorNameMayContainOne() {
        SourceId id = SourceId.parse("purpleair:North:Tower 2");

        assertEquals("purpleair", id.provider());
        assertEquals("North:Tower 2", id.sensor());
    }

    @Test
    void toStringIsTheFormParseReads() {
        assertEquals("purpleair:x", new SourceId("purpleair", "x").toString());
        assertEquals("purpleair:a:b", SourceId.parse("purpleair:a:b").toString());
    }

    @Test
    void parseRejectsAStringWithoutASeparator() {
        assertThrows(IllegalArgumentException.class, () -> SourceId.parse("308702"));
    }

    @Test
    void parseRejectsABlankProviderOrSensor() {
        assertThrows(IllegalArgumentException.class, () -> SourceId.parse(":x"));
        assertThrows(IllegalArgumentException.class, () -> SourceId.parse("purpleair:"));
        assertThrows(IllegalArgumentException.class, () -> SourceId.parse("purpleair: "));
    }

    @Test
    void theProviderMustNotContainTheSeparator() {
        assertThrows(IllegalArgumentException.class, () -> new SourceId("purple:air", "x"));
    }

    @Test
    void tryParseParsesAnId() {
        assertEquals(Optional.of(new SourceId("purpleair", "x")), SourceId.tryParse("purpleair:x"));
    }

    @Test
    void tryParseIsEmptyForAStringThatIsNotAnId() {
        assertTrue(SourceId.tryParse("308702").isEmpty());
        assertTrue(SourceId.tryParse(":x").isEmpty());
        assertTrue(SourceId.tryParse("purpleair:").isEmpty());
        assertTrue(SourceId.tryParse(" :x").isEmpty());
        assertTrue(SourceId.tryParse("purpleair: ").isEmpty());
    }
}
