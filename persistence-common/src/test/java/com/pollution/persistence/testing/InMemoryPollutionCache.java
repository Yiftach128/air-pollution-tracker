package com.pollution.persistence.testing;

import com.pollution.common.json.JsonSupport;
import com.pollution.persistence.IPollutionCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * An {@link IPollutionCache} in a map, for tests: the same contract as the
 * Redis one without the server. Values make the round trip through
 * {@link JsonSupport} as they would on the wire, so a value that does not
 * survive JSON fails here too; lifetimes are measured against the given
 * {@link Clock}, so a test moves time instead of waiting; patterns are
 * Redis globs ({@code *}, {@code ?}, {@code [..]}, {@code \} escaping the
 * next character). Can be told to fail, to test what a service does when
 * its cache is down.
 *
 * @param <T> the value type
 */
public final class InMemoryPollutionCache<T> implements IPollutionCache<T> {

    /** A stored value and when it stops existing; {@code null} for never. */
    private record Entry(String json, Instant expiresAt) {
    }

    private final Class<T> valueType;
    private final Clock clock;
    private final TreeMap<String, Entry> entries = new TreeMap<>();
    private RuntimeException failure;
    private boolean closed;

    /**
     * @param valueType the value type, used to rebuild objects from JSON
     * @param clock     what lifetimes are measured against
     */
    public InMemoryPollutionCache(Class<T> valueType, Clock clock) {
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized void setObjectValue(String key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        failIfToldTo();
        entries.put(key, new Entry(JsonSupport.toJson(value), null));
    }

    @Override
    public synchronized void setObjectValue(String key, T value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
        failIfToldTo();
        entries.put(key, new Entry(JsonSupport.toJson(value), clock.instant().plus(ttl)));
    }

    @Override
    public synchronized Optional<T> getObjectValue(String key) {
        Objects.requireNonNull(key, "key");
        failIfToldTo();
        Entry entry = live(key);
        return entry == null ? Optional.empty() : Optional.of(JsonSupport.fromJson(entry.json(), valueType));
    }

    @Override
    public synchronized List<String> getObjectKeysByPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        failIfToldTo();
        Pattern regex = globToRegex(pattern);
        return keys().stream().filter(key -> regex.matcher(key).matches()).toList();
    }

    @Override
    public synchronized List<T> getObjectValuesByKeys(List<String> keys) {
        Objects.requireNonNull(keys, "keys");
        failIfToldTo();
        List<T> values = new ArrayList<>(keys.size());
        for (String key : keys) {
            Entry entry = live(key);
            if (entry != null) {
                values.add(JsonSupport.fromJson(entry.json(), valueType));
            }
        }
        return values;
    }

    @Override
    public synchronized List<T> getObjectValuesByPattern(String pattern) {
        return getObjectValuesByKeys(getObjectKeysByPattern(pattern));
    }

    @Override
    public synchronized void removeObject(String key) {
        Objects.requireNonNull(key, "key");
        failIfToldTo();
        entries.remove(key);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    // --- what a test may ask or tell ---

    /** Every key that currently exists, in order. */
    public synchronized List<String> keys() {
        dropExpired();
        return List.copyOf(entries.keySet());
    }

    /** When the key stops existing; empty if it does not exist or lives forever. */
    public synchronized Optional<Instant> expiryOf(String key) {
        Entry entry = live(key);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.expiresAt());
    }

    /** Makes every operation throw {@code failure} until told otherwise ({@code null} to work again). */
    public synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    // --- internals ---

    private void failIfToldTo() {
        if (failure != null) {
            throw failure;
        }
    }

    /** The entry under the key if it exists now, dropping it if its lifetime has passed. */
    private Entry live(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            entries.remove(key);
            return null;
        }
        return entry;
    }

    private void dropExpired() {
        for (Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            if (isExpired(it.next().getValue())) {
                it.remove();
            }
        }
    }

    private boolean isExpired(Entry entry) {
        return entry.expiresAt() != null && !entry.expiresAt().isAfter(clock.instant());
    }

    /** Redis glob to a regex over the whole key: {@code *} any run, {@code ?} one character, {@code [..]} a class, {@code \} a literal next. */
    static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '\\' -> {
                    if (i + 1 < glob.length()) {
                        regex.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
                    } else {
                        regex.append(Pattern.quote("\\"));
                    }
                }
                case '[' -> {
                    int end = glob.indexOf(']', i);
                    if (end < 0) {
                        regex.append(Pattern.quote("["));
                    } else {
                        regex.append(glob, i, end + 1);
                        i = end;
                    }
                }
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
