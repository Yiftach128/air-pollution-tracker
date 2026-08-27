package com.pollution.persistence;

import java.util.List;
import java.util.Optional;

/**
 * A durable cache of objects of one type, addressed by string key. Keys are
 * global to the backing store, so callers namespace theirs with a prefix
 * (e.g. {@code analyzer:rolling-average-state:<sensorId>}) and look them up
 * by glob pattern ({@code analyzer:rolling-average-state:*}).
 * <p>
 * Every method throws {@link PollutionCacheException} if the store cannot be
 * reached; callers decide whether that is fatal.
 *
 * @param <T> the value type; must be rebuildable from JSON by
 *            {@code com.pollution.common.json.JsonSupport}
 */
public interface IPollutionCache<T> extends AutoCloseable {

    /** Stores the object under the key, replacing any previous value. */
    void setObjectValue(String key, T value);

    /** The object stored under the key, or empty if there is none. */
    Optional<T> getObjectValue(String key);

    /**
     * Every key matching the glob pattern ({@code *}, {@code ?}, {@code [..]}
     * as in Redis {@code MATCH}); empty if none does.
     */
    List<String> getObjectKeysByPattern(String pattern);

    /** The objects stored under the given keys, in key order; keys with no value are skipped. */
    List<T> getObjectValuesByKeys(List<String> keys);

    /** Shorthand for {@code getObjectValuesByKeys(getObjectKeysByPattern(pattern))}. */
    List<T> getObjectValuesByPattern(String pattern);

    /** Removes the object under the key; does nothing if there is none. */
    void removeObject(String key);

    @Override
    void close();
}
