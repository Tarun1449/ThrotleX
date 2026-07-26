package com.throttlex.config.redis;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns SyncFallbackCache for every cache name.
 *
 * SyncFallbackCache behaviour:
 *   Redis UP   → Redis check → Caffeine sync loader (1 DB call, others wait)
 *   Redis DOWN → straight to DB
 */
public class FallbackCacheManager implements CacheManager {

    private static final Logger log =
            LoggerFactory.getLogger(FallbackCacheManager.class);

    private final RedisCacheManager redisCacheManager;
    private final NoOpCacheManager noOpCacheManager =
            new NoOpCacheManager();

    private final MeterRegistry meterRegistry;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    /**
     * Create cache once, reuse forever.
     */
    private final ConcurrentHashMap<String, Cache> cacheMap =
            new ConcurrentHashMap<>();

    public FallbackCacheManager(
            RedisCacheManager redisCacheManager,
            MeterRegistry meterRegistry,
            io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker) {

        this.redisCacheManager = redisCacheManager;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = circuitBreaker;

        log.info("[FallbackCacheManager] Initialised.");
    }

    @Override
    public Cache getCache(String name) {

        return cacheMap.computeIfAbsent(name, cacheName -> {

            try {

                RedisCache redisCache =
                        (RedisCache) redisCacheManager.getCache(cacheName);

                if (redisCache == null) {

                    log.warn(
                            "[FallbackCacheManager] No cache config found for '{}'",
                            cacheName
                    );

                    return noOpCacheManager.getCache(cacheName);
                }

                log.info(
                        "[FallbackCacheManager] Creating cache '{}'",
                        cacheName
                );

                return new SyncFallbackCache(
                        redisCache,
                        circuitBreaker,
                        meterRegistry
                );

            } catch (Exception e) {

                log.error(
                        "[FallbackCacheManager] Failed creating cache '{}'",
                        cacheName,
                        e
                );

                return noOpCacheManager.getCache(cacheName);
            }
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisCacheManager.getCacheNames();
    }
}