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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Returns SyncFallbackCache for every cache name.
 *
 * SyncFallbackCache behaviour:
 *   Redis UP   → Redis check → Caffeine sync loader (1 DB call, others wait)
 *   Redis DOWN → straight to DB
 */
public class FallbackCacheManager implements CacheManager {

    private static final Logger log = LoggerFactory.getLogger(FallbackCacheManager.class);

    private final RedisCacheManager  redisCacheManager;
    private final NoOpCacheManager   noOpCacheManager = new NoOpCacheManager();
    private final AtomicBoolean      redisAvailable   = new AtomicBoolean(true);
    private final MeterRegistry      meterRegistry;

    public FallbackCacheManager(RedisCacheManager redisCacheManager,  // ← RedisCacheManager (not CacheManager)
                                MeterRegistry meterRegistry) {
        this.redisCacheManager = redisCacheManager;
        this.meterRegistry     = meterRegistry;
        log.info("[FallbackCacheManager] Initialised.");
    }

    @Override
    public Cache getCache(String name) {
        try {
            // RedisCacheManager.getCache() returns RedisCache specifically
            RedisCache redisCache = (RedisCache) redisCacheManager.getCache(name);

            if (redisCache == null) {
                log.warn("[FallbackCacheManager] No cache config found for '{}'. Using NoOp.", name);
                return noOpCacheManager.getCache(name);
            }

            // SyncFallbackCache — handles Redis UP/DOWN + stampede protection
            return new SyncFallbackCache(redisCache, redisAvailable, meterRegistry);

        } catch (Exception e) {
            log.warn("[FallbackCacheManager] Failed to get cache '{}'. Using NoOp. {}", name, e.getMessage());
            redisAvailable.set(false);
            return noOpCacheManager.getCache(name);
        }
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisCacheManager.getCacheNames();
    }

    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }
}
