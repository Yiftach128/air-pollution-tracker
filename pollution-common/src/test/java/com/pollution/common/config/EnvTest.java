package com.pollution.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reading of a {@code .env} file; the process environment itself is not something a test controls. */
class EnvTest {

    private static final String BOM = Character.toString(0xFEFF);

    @Test
    void readsNameValueLinesTrimmed() {
        Map<String, String> values = Env.parseDotenv(List.of("A=1", "B = two "));

        assertEquals(Map.of("A", "1", "B", "two"), values);
    }

    @Test
    void skipsCommentsAndBlankLines() {
        Map<String, String> values = Env.parseDotenv(List.of("# secrets", "", "   ", "A=1"));

        assertEquals(Map.of("A", "1"), values);
    }

    @Test
    void stripsAnExportPrefixAndSurroundingQuotes() {
        Map<String, String> values = Env.parseDotenv(List.of("export A='x y'", "B=\"z\"", "C='unbalanced\""));

        assertEquals(Map.of("A", "x y", "B", "z", "C", "'unbalanced\""), values);
    }

    @Test
    void skipsLinesWithoutASeparatorOrAName() {
        Map<String, String> values = Env.parseDotenv(List.of("no separator", "=orphan", "A=1"));

        assertEquals(Map.of("A", "1"), values);
    }

    @Test
    void stripsAByteOrderMarkFromTheFirstLine() {
        Map<String, String> values = Env.parseDotenv(List.of(BOM + "A=1", "B=2"));

        assertEquals(Map.of("A", "1", "B", "2"), values);
        assertFalse(values.containsKey(BOM + "A"));
    }

    @Test
    void anEmptyValueIsKeptEmptyForLookupToTreatAsUnset() {
        Map<String, String> values = Env.parseDotenv(List.of("A="));

        assertEquals(Map.of("A", ""), values);
    }

    @Test
    void theLastOfARepeatedNameWins() {
        Map<String, String> values = Env.parseDotenv(List.of("A=1", "A=2"));

        assertEquals(Map.of("A", "2"), values);
    }

    @Test
    void aValueMayContainTheSeparator() {
        Map<String, String> values = Env.parseDotenv(List.of("URL=jdbc:postgresql://host/db?a=b"));

        assertEquals(Map.of("URL", "jdbc:postgresql://host/db?a=b"), values);
    }
}
