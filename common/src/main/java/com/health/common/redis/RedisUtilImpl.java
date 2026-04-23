package com.health.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

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
            throw new RuntimeException("Redis SET failed", e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            String value = connection.sync().get(key);
            if (value == null) return null;
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            throw new RuntimeException("Redis GET failed", e);
        }
    }

    @Override
    public Set<String> getKeys(String pattern) {
        try {
            return new HashSet<>(connection.sync().keys(pattern));
        } catch (Exception e) {
            throw new RuntimeException("Redis KEYS failed", e);
        }
    }

    @Override
    public void delete(String key) {
        connection.sync().del(key);
    }

    @Override
    public boolean exists(String key) {
        return connection.sync().exists(key) > 0;
    }

    @Override
    public Long increment(String key, long ttlSeconds) {
        Long count = connection.sync().incr(key);
        if (count == 1) {
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