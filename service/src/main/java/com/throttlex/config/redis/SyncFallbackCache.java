package com.throttlex.config.redis;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.Callable;

@Slf4j
public class SyncFallbackCache implements Cache {

    private final RedisCache redisCache;
    private final CaffeineCache caffeineCache;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;

    private final Counter redisHitCounter;
    private final Counter redisMissCounter;
    private final Counter caffeineFallbackCounter;
    private final Counter dbFallbackCounter;

    public SyncFallbackCache(
            RedisCache redisCache,
            CircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry) {

        this.redisCache = redisCache;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;

        if (meterRegistry != null) {
            this.redisHitCounter = Counter.builder("cache.redis.hit").tag("cache", redisCache.getName()).register(meterRegistry);
            this.redisMissCounter = Counter.builder("cache.redis.miss").tag("cache", redisCache.getName()).register(meterRegistry);
            this.caffeineFallbackCounter = Counter.builder("cache.caffeine.fallback").tag("cache", redisCache.getName()).register(meterRegistry);
            this.dbFallbackCounter = Counter.builder("cache.db.fallback").tag("cache", redisCache.getName()).register(meterRegistry);
        } else {
            this.redisHitCounter = null;
            this.redisMissCounter = null;
            this.caffeineFallbackCounter = null;
            this.dbFallbackCounter = null;
        }

        this.caffeineCache = new CaffeineCache(
                redisCache.getName(),
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<Object, Object>() {
                            @Override
                            public long expireAfterCreate(Object key, Object value, long currentTime) {
                                // Jitter between 60 seconds (1 min) and 150 seconds (2.5 min)
                                long jitterSeconds = java.util.concurrent.ThreadLocalRandom.current().nextLong(60, 151);
                                return java.util.concurrent.TimeUnit.SECONDS.toNanos(jitterSeconds);
                            }
                            @Override
                            public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                                return currentDuration;
                            }
                            @Override
                            public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                                return currentDuration;
                            }
                        })
                        .build(),
                false
        );
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        // 1. Check L1 Caffeine Cache first
        ValueWrapper caffeineVal = caffeineCache.get(key);
        if (caffeineVal != null) {
            increment("cache.caffeine.hit");
            @SuppressWarnings("unchecked")
            T value = (T) caffeineVal.get();
            return value;
        }

        // 2. Check L2 Redis Cache if L1 missed
        try {
            ValueWrapper redisVal = circuitBreaker.executeSupplier(() -> redisCache.get(key));

            if (redisVal != null) {
                increment("cache.redis.hit");
                @SuppressWarnings("unchecked")
                T value = (T) redisVal.get();
                caffeineCache.put(key, value);
                return value;
            }
        } catch (CallNotPermittedException e) {
            // Circuit is open, skip redis instantly
            increment("cache.caffeine.fallback");
            return callCaffeineWithLoader(key, valueLoader);
        } catch (Exception e) {
            log.warn("[SyncFallbackCache] Redis get failed for key '{}'. Falling back to L1 Caffeine Cache.", key, e);
            increment("cache.caffeine.fallback");
            return callCaffeineWithLoader(key, valueLoader);
        }

        increment("cache.redis.miss");
        return callCaffeineWithLoader(key, valueLoader);
    }
    
    private <T> T callCaffeineWithLoader(Object key, Callable<T> valueLoader) {
        return caffeineCache.get(key, () -> {
            T result = callDb(key, valueLoader);
            try {
                circuitBreaker.executeRunnable(() -> redisCache.put(key, result));
            } catch (Exception e) {
                log.warn("[SyncFallbackCache] Redis put failed for key '{}'.", key, e);
            }
            return result;
        });
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        // 1. Check L1 Caffeine Cache first
        ValueWrapper caffeineVal = caffeineCache.get(key);
        if (caffeineVal != null) {
            increment("cache.caffeine.hit");
            return caffeineVal;
        }

        // 2. Check L2 Redis Cache
        try {
            ValueWrapper val = circuitBreaker.executeSupplier(() -> redisCache.get(key));
            if (val != null) {
                increment("cache.redis.hit");
                caffeineCache.put(key, val.get());
                return val;
            } else {
                increment("cache.redis.miss");
            }
            return null;
        } catch (CallNotPermittedException e) {
            increment("cache.caffeine.fallback");
            return null;
        } catch (Exception e) {
            increment("cache.caffeine.fallback");
            return null;
        }
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        // 1. Check L1 Caffeine Cache first
        T caffeineVal = caffeineCache.get(key, type);
        if (caffeineVal != null) {
            increment("cache.caffeine.hit");
            return caffeineVal;
        }

        // 2. Check L2 Redis Cache
        try {
            T val = circuitBreaker.executeSupplier(() -> redisCache.get(key, type));
            if (val != null) {
                increment("cache.redis.hit");
                caffeineCache.put(key, val);
                return val;
            } else {
                increment("cache.redis.miss");
            }
            return null;
        } catch (CallNotPermittedException e) {
            increment("cache.db.fallback");
            return null;
        } catch (Exception e) {
            increment("cache.db.fallback");
            return null;
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        caffeineCache.put(key, value);
        try {
            circuitBreaker.executeRunnable(() -> redisCache.put(key, value));
        } catch (CallNotPermittedException e) {
            // Circuit open, do nothing
        } catch (Exception e) {
            log.warn("[SyncFallbackCache] Redis put failed for key '{}'.", key, e);
        }
    }

    @Override
    public void evict(Object key) {
        caffeineCache.evict(key);
        try {
            circuitBreaker.executeRunnable(() -> redisCache.evict(key));
        } catch (CallNotPermittedException e) {
            // Circuit open
        } catch (Exception e) {
            log.warn("[SyncFallbackCache] Redis evict failed for key '{}'.", key, e);
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        caffeineCache.put(key, value);
        try {
            return circuitBreaker.executeSupplier(() -> redisCache.putIfAbsent(key, value));
        } catch (CallNotPermittedException e) {
            return null;
        } catch (Exception e) {
            log.warn("[SyncFallbackCache] Redis putIfAbsent failed for key '{}'.", key, e);
            return null;
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        caffeineCache.evictIfPresent(key);
        try {
            return circuitBreaker.executeSupplier(() -> redisCache.evictIfPresent(key));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void clear() {
        caffeineCache.clear();
        try {
            circuitBreaker.executeRunnable(redisCache::clear);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean invalidate() {
        caffeineCache.invalidate();
        try {
            return circuitBreaker.executeSupplier(redisCache::invalidate);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return redisCache.getName();
    }

    @Override
    public Object getNativeCache() {
        return redisCache.getNativeCache();
    }

    @Nullable
    private <T> T callDb(Object key, Callable<T> valueLoader) {
        increment("cache.db.fallback");
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private void increment(String metric) {
        if (redisHitCounter == null) return;
        switch (metric) {
            case "cache.redis.hit" -> redisHitCounter.increment();
            case "cache.redis.miss" -> redisMissCounter.increment();
            case "cache.caffeine.fallback" -> caffeineFallbackCounter.increment();
            case "cache.db.fallback" -> dbFallbackCounter.increment();
            default -> meterRegistry.counter(metric, "cache", getName()).increment();
        }
    }
}