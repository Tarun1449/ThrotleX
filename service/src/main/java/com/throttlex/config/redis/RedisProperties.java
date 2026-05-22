package com.throttlex.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "spring.data.redis")
public record RedisProperties(
        String host,
        int port,
        String password,
        int database,
        Ssl ssl,
        Duration timeout,
        Duration connectTimeout,
        Lettuce lettuce
) {

    public record Ssl(boolean enabled) {
    }

    public record Lettuce(Pool pool) {
    }

    public record Pool(
            boolean enabled,
            int maxActive,
            int maxIdle,
            int minIdle,
            Duration maxWait,
            Duration timeBetweenEvictionRuns
    ) {
    }
}
