package com.health.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface RedisUtil {

    <T> void set(String key, T value, long ttlSeconds);

    <T> CompletableFuture<Void> setAsync(String key, T value, long ttlSeconds);

    <T> T get(String key, Class<T> type);

    <T> CompletableFuture<T> getAsync(String key, Class<T> type);

    <T> T get(String key, TypeReference<T> typeRef);

    <T> CompletableFuture<T> getAsync(String key, TypeReference<T> typeRef);

    Set<String> getKeys(String pattern);

    void delete(String key);

    CompletableFuture<Void> deleteAsync(String key);

    boolean exists(String key);

    Long increment(String key, long ttlSeconds);

    String tryLock(String key, long ttlMillis);

    boolean releaseLock(String key, String token);
}