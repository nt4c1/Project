package com.health.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class RedisUtilImpl implements RedisUtil {

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
            String json = objectMapper.writeValueAsString(value);
            connection.sync().setex(key, ttlSeconds, json);
        } catch (Exception e) {
            log.error("Redis SET failed for key: {}", key, e);
            throw new RuntimeException("Redis SET failed", e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, T value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return connection.async().setex(key, ttlSeconds, json)
                    .toCompletableFuture()
                    .thenAccept(s -> {});
        } catch (Exception e) {
            log.error("Redis SET async failed for key: {}", key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            String value = connection.sync().get(key);
            if (value == null) return null;
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.error("Redis GET failed for key: {}", key, e);
            throw new RuntimeException("Redis GET failed", e);
        }
    }

    @Override
    public <T> CompletableFuture<T> getAsync(String key, Class<T> type) {
        return connection.async().get(key).toCompletableFuture().thenApply(value -> {
            if (value == null) return null;
            try {
                return objectMapper.readValue(value, type);
            } catch (Exception e) {
                log.error("Redis GET async failed for key: {}", key, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Set<String> getKeys(String pattern) {
        try {
            return new HashSet<>(connection.sync().keys(pattern));
        } catch (Exception e) {
            log.error("Redis KEYS failed for pattern: {}", pattern, e);
            throw new RuntimeException("Redis KEYS failed", e);
        }
    }

    @Override
    public void delete(String key) {
        connection.sync().del(key);
    }

    @Override
    public CompletableFuture<Void> deleteAsync(String key) {
        return connection.async().del(key).toCompletableFuture().thenAccept(l -> {});
    }

    @Override
    public boolean exists(String key) {
        return connection.sync().exists(key) > 0;
    }

    @Override
    public Long increment(String key, long ttlSeconds) {
        Long count = connection.sync().incr(key);
        if (count != null && count == 1) {
            connection.sync().expire(key, ttlSeconds);
        }
        return count;
    }

    @Override
    public boolean tryLock(String key, long ttlMillis) {
        return "OK".equals(
                connection.sync().set(key, "1",
                        SetArgs.Builder.nx().px(ttlMillis))
        );
    }

    @Override
    public void releaseLock(String key) {
        connection.sync().del(key);
    }
}