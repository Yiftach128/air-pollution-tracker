package com.pollution.datacollector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundRobinApiKeyProviderTest {

    @Test
    void handsOutTheKeysInTurnAndStartsOver() {
        IApiKeyProvider keys = new RoundRobinApiKeyProvider(List.of("a", "b", "c"));

        assertEquals(List.of("a", "b", "c", "a", "b"), List.of(keys.next(), keys.next(), keys.next(), keys.next(), keys.next()));
        assertEquals(3, keys.size());
    }

    @Test
    void aSingleKeyIsHandedOutEveryTime() {
        IApiKeyProvider keys = new RoundRobinApiKeyProvider(List.of("only"));

        assertEquals("only", keys.next());
        assertEquals("only", keys.next());
        assertEquals(1, keys.size());
    }

    @Test
    void keepsItsOwnCopyOfTheKeys() {
        List<String> given = new ArrayList<>(List.of("a"));
        IApiKeyProvider keys = new RoundRobinApiKeyProvider(given);

        given.add("b");

        assertEquals(1, keys.size());
    }

    @Test
    void needsAtLeastOneKey() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinApiKeyProvider(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinApiKeyProvider(null));
    }
}
