package com.throttlex.config.redis;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;

@Configuration
public class CacheConfiguration {


    @Bean
    @Primary
    public CacheManager cacheManager(
            RedisCacheManager redisCacheManager,
            MeterRegistry meterRegistry,
            RedisHealthMonitor healthMonitor) {

        return new FallbackCacheManager(
                redisCacheManager,
                meterRegistry,
                healthMonitor
        );
    }
}