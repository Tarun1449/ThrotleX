package com.throttlex.config.redis;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

    public SyncFallbackCache(
            RedisCache redisCache,
            CircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry) {

        this.redisCache = redisCache;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;

        this.caffeineCache = new CaffeineCache(
                redisCache.getName(),
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofMillis(500))
                        .build(),
                false
        );
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            ValueWrapper redisVal = circuitBreaker.executeSupplier(() -> redisCache.get(key));

            if (redisVal != null) {
                increment("cache.redis.hit");
                @SuppressWarnings("unchecked")
                T value = (T) redisVal.get();
                return value;
            }
        } catch (CallNotPermittedException e) {
            // Circuit is open, skip redis instantly
            increment("cache.db.fallback");
            return callCaffeineWithLoader(key, valueLoader);
        } catch (Exception e) {
            log.warn("[SyncFallbackCache] Redis get failed for key '{}'. Falling back to DB.", key, e);
            increment("cache.db.fallback");
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
        try {
            ValueWrapper val = circuitBreaker.executeSupplier(() -> redisCache.get(key));
            if (val != null) {
                increment("cache.redis.hit");
            } else {
                increment("cache.redis.miss");
            }
            return val;
        } catch (CallNotPermittedException e) {
            increment("cache.db.fallback");
            return null;
        } catch (Exception e) {
            increment("cache.db.fallback");
            return null;
        }
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        try {
            T val = circuitBreaker.executeSupplier(() -> redisCache.get(key, type));
            if (val != null) {
                increment("cache.redis.hit");
            } else {
                increment("cache.redis.miss");
            }
            return val;
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