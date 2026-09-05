package com.pollution.persistence.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.common.testing.MutableClock;
import com.pollution.persistence.PollutionCacheException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The cache fake every store test relies on must behave like the Redis one. */
class InMemoryPollutionCacheTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");

    private final MutableClock clock = MutableClock.at(T0);
    private final InMemoryPollutionCache<PollutionData> cache = new InMemoryPollutionCache<>(PollutionData.class, clock);

    private static PollutionData reading(String source, double value) {
        return new PollutionData("Tel Aviv", source, Pollutant.PM2_5, value, T0);
    }

    @Test
    void storesAndReturnsAnEqualValue() {
        PollutionData reading = reading("purpleair:x", 12.5);

        cache.setObjectValue("k", reading);

        assertEquals(Optional.of(reading), cache.getObjectValue("k"));
        assertEquals(Optional.empty(), cache.getObjectValue("missing"));
    }

    @Test
    void aValueWithALifetimeIsGoneOnceItPasses() {
        cache.setObjectValue("k", reading("purpleair:x", 1), Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(9));
        assertTrue(cache.getObjectValue("k").isPresent());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(cache.getObjectValue("k").isEmpty());
        assertEquals(List.of(), cache.keys());
    }

    @Test
    void storingAKeyAgainRestartsItsLifetime() {
        cache.setObjectValue("k", reading("purpleair:x", 1), Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(8));
        cache.setObjectValue("k", reading("purpleair:x", 2), Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(8));

        assertEquals(Optional.of(reading("purpleair:x", 2)), cache.getObjectValue("k"));
        assertEquals(Optional.of(T0.plus(Duration.ofMinutes(18))), cache.expiryOf("k"));
    }

    @Test
    void aValueWithoutALifetimeStays() {
        cache.setObjectValue("k", reading("purpleair:x", 1));
        clock.advance(Duration.ofDays(365));

        assertTrue(cache.getObjectValue("k").isPresent());
        assertEquals(Optional.empty(), cache.expiryOf("k"));
    }

    @Test
    void matchesKeysByGlobInKeyOrder() {
        for (String key : List.of("a:2", "a:1", "b:1", "a:12")) {
            cache.setObjectValue(key, reading(key, 1));
        }

        assertEquals(List.of("a:1", "a:12", "a:2"), cache.getObjectKeysByPattern("a:*"));
        assertEquals(List.of("a:1", "a:2"), cache.getObjectKeysByPattern("a:?"));
        assertEquals(List.of("a:1", "b:1"), cache.getObjectKeysByPattern("[ab]:1"));
        assertEquals(List.of(), cache.getObjectKeysByPattern("c:*"));
    }

    @Test
    void aBackslashMakesTheNextCharacterLiteral() {
        cache.setObjectValue("s*", reading("s*", 1));
        cache.setObjectValue("sX", reading("sX", 2));

        assertEquals(List.of("s*"), cache.getObjectKeysByPattern("s\\*"));
        assertEquals(List.of("s*", "sX"), cache.getObjectKeysByPattern("s*"));
    }

    @Test
    void regexCharactersInAPatternAreLiteral() {
        cache.setObjectValue("a.b", reading("a.b", 1));
        cache.setObjectValue("axb", reading("axb", 2));

        assertEquals(List.of("a.b"), cache.getObjectKeysByPattern("a.b"));
    }

    @Test
    void valuesByKeysFollowTheKeysAndSkipMissingOnes() {
        cache.setObjectValue("k1", reading("one", 1));
        cache.setObjectValue("k2", reading("two", 2));

        List<PollutionData> values = cache.getObjectValuesByKeys(List.of("k2", "missing", "k1"));

        assertEquals(List.of(reading("two", 2), reading("one", 1)), values);
    }

    @Test
    void valuesByPatternAreTheMatchingKeysValues() {
        cache.setObjectValue("a:1", reading("one", 1));
        cache.setObjectValue("b:1", reading("two", 2));

        assertEquals(List.of(reading("one", 1)), cache.getObjectValuesByPattern("a:*"));
    }

    @Test
    void removesAValue() {
        cache.setObjectValue("k", reading("purpleair:x", 1));

        cache.removeObject("k");
        cache.removeObject("k");

        assertTrue(cache.getObjectValue("k").isEmpty());
    }

    @Test
    void rejectsANonPositiveLifetime() {
        assertThrows(IllegalArgumentException.class, () -> cache.setObjectValue("k", reading("x", 1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> cache.setObjectValue("k", reading("x", 1), Duration.ofSeconds(-1)));
    }

    @Test
    void failsEveryOperationWhileToldTo() {
        cache.setObjectValue("k", reading("purpleair:x", 1));
        cache.failWith(new PollutionCacheException("redis is down", null));

        assertThrows(PollutionCacheException.class, () -> cache.getObjectValue("k"));
        assertThrows(PollutionCacheException.class, () -> cache.setObjectValue("k", reading("x", 2)));
        assertThrows(PollutionCacheException.class, () -> cache.getObjectKeysByPattern("*"));

        cache.failWith(null);
        assertEquals(Optional.of(reading("purpleair:x", 1)), cache.getObjectValue("k"));
    }

    @Test
    void closingIsRemembered() {
        cache.close();

        assertTrue(cache.isClosed());
    }
}
