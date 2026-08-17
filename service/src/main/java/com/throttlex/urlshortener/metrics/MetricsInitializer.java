package com.throttlex.urlshortener.metrics;

import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MetricsInitializer {

    @PostConstruct
    public void initMetrics() {
        // Pre-register counters so Prometheus exports 0 value on startup instead of No Data
        Metrics.counter("throttlex.ratelimit.allowed");
        Metrics.counter("throttlex.ratelimit.blocked");
        Metrics.counter("throttlex.bloom.filter.rejected");
        Metrics.counter("cache.db.fallback", "cache", "urls");
        Metrics.counter("cache.db.fallback", "cache", "rate_limit_configs");
        Metrics.counter("cache.redis.hit", "cache", "urls");
        Metrics.counter("cache.redis.hit", "cache", "rate_limit_configs");
        Metrics.counter("cache.redis.miss", "cache", "urls");
        Metrics.counter("cache.redis.miss", "cache", "rate_limit_configs");
    }
}
