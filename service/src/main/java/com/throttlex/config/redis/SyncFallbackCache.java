package com.throttlex.config.redis;


import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Smart cache that combines Redis + Caffeine + DB fallback.
 *
 * Redis UP   → check Redis first, if miss use Caffeine (sync=true safe)
 *              → only ONE thread loads from DB, others wait
 * Redis DOWN → skip everything, go straight to DB
 *
 * Flow:
 *   Request
 *     │
 *     ├── Redis UP
 *     │     ├── Redis HIT  → return cached value
 *     │     └── Redis MISS → Caffeine.get(key, loader)  ← sync, only 1 DB call
 *     │                          │
 *     │                          └── stores in both Caffeine + Redis
 *     │
 *     └── Redis DOWN → valueLoader.call() directly → DB hit (no lock)
 */
@Slf4j
public class SyncFallbackCache implements Cache {


    private final RedisCache redisCache;
    private final CaffeineCache caffeineCache;   // handles sync — only 1 DB call
    private final AtomicBoolean redisAvailable;
    private final MeterRegistry meterRegistry;

    public SyncFallbackCache(RedisCache redisCache,
                             AtomicBoolean redisAvailable,
                             MeterRegistry meterRegistry) {
        this.redisCache     = redisCache;
        this.redisAvailable = redisAvailable;
        this.meterRegistry  = meterRegistry;

        // Caffeine — short TTL, just enough to collapse concurrent misses
        // Acts as a stampede shield, not a long-term cache
        this.caffeineCache = new CaffeineCache(
                redisCache.getName(),
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(java.time.Duration.ofMillis(500))
                        .build(),
                false   // allowNullValues
        );
    }

    // ── get(key, Callable) — this is what sync=true triggers ─────────────────

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        if (!redisAvailable.get()) {
            // Redis DOWN — but STILL use Caffeine to prevent local stampedes!
            increment("cache.db.fallback");
            return caffeineCache.get(key, () -> {
                return callDb(key, valueLoader);
                // NO redis put here since Redis is down
            });
        }

        // Redis UP — check Redis first
        try {
            ValueWrapper redisVal = redisCache.get(key);
            if (redisVal != null) {
                redisAvailable.set(true);
                increment("cache.redis.hit");
                return (T) redisVal.get();
            }
        } catch (Exception e) {
            // Redis failed mid-check — flip state, go to DB
            redisAvailable.set(false);
            log.warn("[SyncFallbackCache] Redis failed on get '{}'. Falling back to DB. {}",
                    key, e.getMessage());
            increment("cache.db.fallback");
            return callDb(key, valueLoader);
        }

        // Redis MISS — use Caffeine to load with sync guarantee
        // Only ONE thread calls valueLoader, others wait for the result
        increment("cache.redis.miss");
        return caffeineCache.get(key, () -> {
            // This block runs for only ONE thread — others wait here
            T result = valueLoader.call();

            // Store in Redis for cross-instance sharing
            try {
                redisCache.put(key, result);
                redisAvailable.set(true);
            } catch (Exception e) {
                redisAvailable.set(false);
                log.warn("[SyncFallbackCache] Redis put failed for '{}'. {}", key, e.getMessage());
            }

            return result;
        });
    }

    // ── get(key) → ValueWrapper ───────────────────────────────────────────────

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        if (!redisAvailable.get()) {
            return null;  // miss → @Cacheable calls get(key, Callable)
        }
        try {
            ValueWrapper val = redisCache.get(key);
            redisAvailable.set(true);
            if (val != null) increment("cache.redis.hit");
            else increment("cache.redis.miss");
            return val;
        } catch (Exception e) {
            redisAvailable.set(false);
            increment("cache.db.fallback");
            return null;
        }
    }

    // ── get(key, type) ────────────────────────────────────────────────────────

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        if (!redisAvailable.get()) return null;
        try {
            T val = redisCache.get(key, type);
            redisAvailable.set(true);
            if (val != null) increment("cache.redis.hit");
            else increment("cache.redis.miss");
            return val;
        } catch (Exception e) {
            redisAvailable.set(false);
            increment("cache.db.fallback");
            return null;
        }
    }

    // ── put ───────────────────────────────────────────────────────────────────

    @Override
    public void put(Object key, @Nullable Object value) {
        caffeineCache.put(key, value);  // always update local
        if (!redisAvailable.get()) return;
        try {
            redisCache.put(key, value);
            redisAvailable.set(true);
        } catch (Exception e) {
            redisAvailable.set(false);
        }
    }

    // ── evict ─────────────────────────────────────────────────────────────────

    @Override
    public void evict(Object key) {
        caffeineCache.evict(key);
        if (!redisAvailable.get()) return;
        try {
            redisCache.evict(key);
            redisAvailable.set(true);
        } catch (Exception e) {
            redisAvailable.set(false);
        }
    }

    // ── putIfAbsent ───────────────────────────────────────────────────────────

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        if (!redisAvailable.get()) return null;
        try {
            ValueWrapper result = redisCache.putIfAbsent(key, value);
            redisAvailable.set(true);
            return result;
        } catch (Exception e) {
            redisAvailable.set(false);
            return null;
        }
    }

    // ── evictIfPresent ────────────────────────────────────────────────────────

    @Override
    public boolean evictIfPresent(Object key) {
        caffeineCache.evictIfPresent(key);
        if (!redisAvailable.get()) return false;
        try {
            boolean result = redisCache.evictIfPresent(key);
            redisAvailable.set(true);
            return result;
        } catch (Exception e) {
            redisAvailable.set(false);
            return false;
        }
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Override
    public void clear() {
        caffeineCache.clear();
        if (!redisAvailable.get()) return;
        try {
            redisCache.clear();
            redisAvailable.set(true);
        } catch (Exception e) {
            redisAvailable.set(false);
        }
    }

    // ── invalidate ────────────────────────────────────────────────────────────

    @Override
    public boolean invalidate() {
        caffeineCache.invalidate();
        if (!redisAvailable.get()) return false;
        try {
            boolean result = redisCache.invalidate();
            redisAvailable.set(true);
            return result;
        } catch (Exception e) {
            redisAvailable.set(false);
            return false;
        }
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public String getName() { return redisCache.getName(); }

    @Override
    public Object getNativeCache() { return redisCache.getNativeCache(); }

    // ── Internals ─────────────────────────────────────────────────────────────

    @Nullable
    private <T> T callDb(Object key, Callable<T> valueLoader) {
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private void increment(String metric) {
        if (meterRegistry != null) {
            meterRegistry.counter(metric, "cache", getName()).increment();
        }
    }
}