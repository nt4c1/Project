package com.health.common.redis;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

public interface RedisUtil {

    <T> void set(String key, T value, long ttlSeconds);

    <T> CompletableFuture<Void> setAsync(String key, T value, long ttlSeconds);

    <T> T get(String key, Class<T> type);

    <T> CompletableFuture<T> getAsync(String key, Class<T> type);

    Set<String> getKeys(String pattern);

    void delete(String key);

    CompletableFuture<Void> deleteAsync(String key);

    boolean exists(String key);

    Long increment(String key, long ttlSeconds);

    boolean tryLock(String key, long ttlMillis);

    void releaseLock(String key);
}