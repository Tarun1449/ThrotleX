package com.throttlex.config.redis;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHealthMonitor {
    private final StringRedisTemplate redisTemplate;

    private final AtomicBoolean redisAvailable =
            new AtomicBoolean(true);

    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }


    @Scheduled(fixedDelay = 5000)
    public void checkRedisHealth() {
        boolean previousState = redisAvailable.get();
        try {
            RedisConnectionFactory connectionFactory =
                    redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                throw new IllegalStateException(
                        "RedisConnectionFactory not configured"
                );
            }
            try (RedisConnection connection =
                         connectionFactory.getConnection()) {
                String response = connection.ping();
                boolean healthy =
                        "PONG".equalsIgnoreCase(response);
                redisAvailable.set(healthy);
                if (!previousState && healthy) {
                    log.info("Redis recovered.");
                }
            }

        } catch (Exception ex) {
            redisAvailable.set(false);
            if (previousState) {
                log.error("Redis became unavailable.", ex);
            }
        }
    }
}
