package com.pollution.datacollector.entities.purpleair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PurpleAirSensorTest {

    @Test
    void parsesTheSensorIndexAndTheCity() {
        assertEquals(new PurpleAirSensor(308702, "Ganei Ayalon"), PurpleAirSensor.parse("308702=Ganei Ayalon"));
    }

    @Test
    void ignoresWhitespaceAroundBothParts() {
        assertEquals(new PurpleAirSensor(1, "Tel Aviv"), PurpleAirSensor.parse(" 1 = Tel Aviv "));
    }

    @Test
    void toStringIsTheFormParseReads() {
        PurpleAirSensor sensor = new PurpleAirSensor(308702, "Ganei Ayalon");

        assertEquals("308702=Ganei Ayalon", sensor.toString());
        assertEquals(sensor, PurpleAirSensor.parse(sensor.toString()));
    }

    @Test
    void theCityMayContainTheSeparator() {
        assertEquals(new PurpleAirSensor(1, "A=B"), PurpleAirSensor.parse("1=A=B"));
    }

    @Test
    void rejectsTextThatIsNotASensor() {
        assertThrows(IllegalArgumentException.class, () -> PurpleAirSensor.parse("308702"));
        assertThrows(IllegalArgumentException.class, () -> PurpleAirSensor.parse("abc=Tel Aviv"));
        assertThrows(IllegalArgumentException.class, () -> PurpleAirSensor.parse("308702="));
        assertThrows(IllegalArgumentException.class, () -> PurpleAirSensor.parse("=Tel Aviv"));
    }

    @Test
    void needsAPositiveIndexAndACity() {
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensor(0, "Tel Aviv"));
        assertThrows(IllegalArgumentException.class, () -> new PurpleAirSensor(1, " "));
        assertThrows(NullPointerException.class, () -> new PurpleAirSensor(1, null));
    }
}
