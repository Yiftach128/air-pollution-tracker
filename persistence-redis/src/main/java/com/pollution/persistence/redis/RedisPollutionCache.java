package com.pollution.persistence.redis;

import com.pollution.common.PollutionLogger;
import com.pollution.common.json.JsonSupport;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.PollutionCacheException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * An {@link IPollutionCache} where every object is its own Redis key holding
 * the JSON produced by {@link JsonSupport}. Lifetimes map onto Redis key
 * expiry ({@code SET ... PX}). Pattern lookups use {@code SCAN} rather than
 * {@code KEYS}, so they never block the server.
 *
 * @param <T> the value type
 */
public class RedisPollutionCache<T> implements IPollutionCache<T> {

    private static final Logger logger = PollutionLogger.getLogger(RedisPollutionCache.class);
    /** Hint for how many keys each SCAN round trip examines. */
    private static final int SCAN_BATCH = 500;

    private final JedisPooled redis;
    private final Class<T> valueType;

    /**
     * @param host      Redis host
     * @param port      Redis port
     * @param valueType the value type, used to rebuild objects from JSON
     */
    public RedisPollutionCache(String host, int port, Class<T> valueType) {
        Objects.requireNonNull(host, "host");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.redis = new JedisPooled(host, port);
        logger.info("cache of {} backed by redis at {}:{}", valueType.getSimpleName(), host, port);
    }

    @Override
    public void setObjectValue(String key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        String json = JsonSupport.toJson(value);
        try {
            redis.set(key, json);
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to set key " + key, e);
        }
    }

    @Override
    public void setObjectValue(String key, T value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
        String json = JsonSupport.toJson(value);
        try {
            redis.set(key, json, SetParams.setParams().px(ttl.toMillis()));
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to set key " + key + " with ttl " + ttl, e);
        }
    }

    @Override
    public Optional<T> getObjectValue(String key) {
        Objects.requireNonNull(key, "key");
        String json;
        try {
            json = redis.get(key);
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to get key " + key, e);
        }
        return json == null ? Optional.empty() : Optional.of(JsonSupport.fromJson(json, valueType));
    }

    @Override
    public List<String> getObjectKeysByPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        ScanParams params = new ScanParams().match(pattern).count(SCAN_BATCH);
        // SCAN may return a key more than once, so collect into a set
        Set<String> keys = new LinkedHashSet<>();
        String cursor = ScanParams.SCAN_POINTER_START;
        try {
            do {
                ScanResult<String> result = redis.scan(cursor, params);
                keys.addAll(result.getResult());
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to scan keys matching " + pattern, e);
        }
        return List.copyOf(keys);
    }

    @Override
    public List<T> getObjectValuesByKeys(List<String> keys) {
        Objects.requireNonNull(keys, "keys");
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> jsons;
        try {
            jsons = redis.mget(keys.toArray(String[]::new));
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to get " + keys.size() + " keys", e);
        }
        List<T> values = new ArrayList<>(jsons.size());
        for (String json : jsons) {
            if (json != null) {
                values.add(JsonSupport.fromJson(json, valueType));
            }
        }
        return values;
    }

    @Override
    public List<T> getObjectValuesByPattern(String pattern) {
        return getObjectValuesByKeys(getObjectKeysByPattern(pattern));
    }

    @Override
    public void removeObject(String key) {
        Objects.requireNonNull(key, "key");
        try {
            redis.del(key);
        } catch (JedisException e) {
            throw new PollutionCacheException("failed to remove key " + key, e);
        }
    }

    @Override
    public void close() {
        redis.close();
    }
}
