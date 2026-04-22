package com.health.common.redis;

public interface RedisUtil {

    <T> void set(String key, T value, long ttlSeconds);

    <T> T get(String key, Class<T> type);

    void delete(String key);

    boolean exists(String key);

    Long increment(String key, long ttlSeconds);

    boolean tryLock(String key, long ttlMillis);

    void releaseLock(String key);
}