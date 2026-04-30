package com.health.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Singleton
public class RedisUtilImpl implements RedisUtil {
    //Lua script for atomic INCR + conditional EXPIRE  Redis treats this as single
    private static final String INCR_WITH_TTL_SCRIPT = """
            local v = redis.call('INCR', KEYS[1])
            if v == 1 and tonumber(ARGV[1]) > 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return v
            """;

    //Lua script for releasing lock
    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;

    public RedisUtilImpl(StatefulRedisConnection<String, String> connection,
                         ObjectMapper objectMapper) {
        this.connection = connection;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> void set(String key, T value, long ttlSeconds) {
        try {
            if (value == null) {
                delete(key);
                return;
            }
            String val = serialize(value);
            if (ttlSeconds > 0) {
                connection.sync().setex(key, ttlSeconds, val);
            } else {
                connection.sync().set(key, val);
            }
        } catch (Exception e) {
            log.error("Redis SET failed for key: {}", key, e);
            throw new RuntimeException("Redis SET failed", e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, T value, long ttlSeconds) {
        if (value == null) {
            return deleteAsync(key);
        }
        try {
            String val = serialize(value);
            RedisFuture<String> future = (ttlSeconds > 0)
                    ? connection.async().setex(key, ttlSeconds, val)
                    : connection.async().set(key, val);

            return future.toCompletableFuture()
                    .thenAccept(s -> {})
                    .exceptionally(e -> {
                        log.error("Redis SET async failed for key: {}", key, e);
                        throw new CompletionException(e);
                    });
        } catch (Exception e) {
            log.error("Redis SET async serialization failed for key: {}", key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            String value = connection.sync().get(key);
            return deserialize(value, type);
        } catch (Exception e) {
            log.error("Redis GET failed for key: {}, type: {}", key, type.getSimpleName(), e);
            return null; // allow DB fallback
        }
    }

    @Override
    public <T> CompletableFuture<T> getAsync(String key, Class<T> type) {
        return connection.async().get(key).toCompletableFuture()
                .thenApply(value -> {
                    try {
                        return deserialize(value, type);
                    } catch (Exception e) {
                        log.error("Redis GET async deserialization failed for key: {}, type: {}",
                                key, type.getSimpleName(), e);
                        throw new CompletionException(e);
                    }
                })
                .exceptionally(e -> {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (CompletionException) e;
                    }
                    log.error("Redis GET async transport error for key: {}", key, e);
                    return null;
                });
    }

    @Override
    public <T> T get(String key, TypeReference<T> typeRef) {
        try {
            String value = connection.sync().get(key);
            return deserialize(value, typeRef);
        } catch (Exception e) {
            log.error("Redis GET failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public <T> CompletableFuture<T> getAsync(String key, TypeReference<T> typeRef) {
        return connection.async().get(key).toCompletableFuture()
                .thenApply(value -> {
                    try {
                        return deserialize(value, typeRef);
                    } catch (Exception e) {
                        log.error("Redis GET async deserialization failed for key: {}", key, e);
                        throw new CompletionException(e);
                    }
                })
                .exceptionally(e -> {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (CompletionException) e;
                    }
                    log.error("Redis GET async transport error for key: {}", key, e);
                    return null;
                });
    }

    @Override
    public Set<String> getKeys(String pattern) {
        try {
            List<String> result = new ArrayList<>();
            ScanArgs args = ScanArgs.Builder.matches(pattern).limit(200);
            KeyScanCursor<String> cursor = connection.sync().scan(ScanCursor.INITIAL, args);
            result.addAll(cursor.getKeys());

            while (!cursor.isFinished()) {
                cursor = connection.sync().scan(cursor, args);
                result.addAll(cursor.getKeys());
            }
            return new HashSet<>(result);
        } catch (Exception e) {
            log.error("Redis SCAN failed for pattern: {}", pattern, e);
            return new HashSet<>();
        }
    }

    @Override
    public void delete(String key) {
        try {
            connection.sync().del(key);
        } catch (Exception e) {
            log.error("Redis DEL failed for key: {}", key, e);
        }
    }

    @Override
    public CompletableFuture<Void> deleteAsync(String key) {
        return connection.async().del(key)
                .toCompletableFuture()
                .thenAccept(l -> {})
                .exceptionally(e -> {
                    log.error("Redis DEL async failed for key: {}", key, e);
                    throw new CompletionException(e);
                });
    }

    // -------------------------------------------------------------------------
    // EXISTS
    // -------------------------------------------------------------------------

    @Override
    public boolean exists(String key) {
        try {
            Long count = connection.sync().exists(key);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Redis EXISTS failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long increment(String key, long ttlSeconds) {
        try {
            return (Long) connection.sync().eval(
                    INCR_WITH_TTL_SCRIPT,
                    ScriptOutputType.INTEGER,
                    new String[]{key},
                    String.valueOf(ttlSeconds)
            );
        } catch (Exception e) {
            log.error("Redis INCR failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public String tryLock(String key, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be > 0 to prevent indefinite locks");
        }
        try {
            String token = UUID.randomUUID().toString();
            String result = connection.sync().set(key, token, SetArgs.Builder.nx().px(ttlMillis));
            return "OK".equals(result) ? token : null;
        } catch (Exception e) {
            log.error("Redis tryLock failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public boolean releaseLock(String key, String token) {
        try {
            Long result = (Long) connection.sync().eval(
                    RELEASE_LOCK_SCRIPT,
                    ScriptOutputType.INTEGER,
                    new String[]{key},
                    token
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Redis releaseLock failed for key: {}", key, e);
            return false;
        }
    }

    private <T> String serialize(T value) throws Exception {
        return (value instanceof String s) ? s : objectMapper.writeValueAsString(value);
    }

    private <T> T deserialize(String value, Class<T> type) throws Exception {
        if (value == null) return null;
        if (type == String.class) return type.cast(value);
        return objectMapper.readValue(value, type);
    }

    private <T> T deserialize(String value, TypeReference<T> typeRef) throws Exception {
        if (value == null) return null;
        return objectMapper.readValue(value, typeRef);
    }
}