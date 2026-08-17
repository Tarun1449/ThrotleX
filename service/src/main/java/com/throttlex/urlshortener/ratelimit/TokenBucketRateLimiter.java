package com.throttlex.urlshortener.ratelimit;

import com.throttlex.ratelimit.entity.RateLimitConfig;
import com.throttlex.ratelimit.service.RateLimitConfigService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
public class TokenBucketRateLimiter implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;
    private final RateLimitConfigService configService;

    // Fallback limits if nothing is configured in the DB
    private static final int DEFAULT_MAX_TOKENS = 10;
    private static final int DEFAULT_REFILL_RATE = 2; // Tokens per second

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate, RateLimitConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        
        // Load the Lua script once during initialization
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.redisScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String shortCode, String clientIp) {
        // The rate limit key is now scoped to BOTH the shortCode and the IP address
        // so one user cannot consume all tokens for a specific URL.
        String redisKey = "ratelimit:token_bucket:" + shortCode + ":" + clientIp;
        
        List<String> keys = Collections.singletonList(redisKey);
        
        // 1. Fetch Dynamic Configuration from Redis Cache (or DB)
        RateLimitConfig config = configService.getConfigByShortCode(shortCode);
        
        int capacity = DEFAULT_MAX_TOKENS;
        int windowSeconds = 60;
        
        if (config != null) {
            capacity = config.getLimitCapacity();
            windowSeconds = config.getWindowSeconds();
        }
        
        // Args: [capacity, window_seconds, current_timestamp_in_seconds]
        long currentTimestamp = Instant.now().getEpochSecond();
        
        org.slf4j.LoggerFactory.getLogger(TokenBucketRateLimiter.class).debug(
                "Hitting Redis TokenBucket script for key: {} | Capacity: {}, Window: {}s, Timestamp: {}", 
                redisKey, capacity, windowSeconds, currentTimestamp);

        Long result = redisTemplate.execute(
                redisScript,
                keys,
                String.valueOf(capacity),
                String.valueOf(windowSeconds),
                String.valueOf(currentTimestamp)
        );

        boolean allowed = result != null && result == 1L;
        org.slf4j.LoggerFactory.getLogger(TokenBucketRateLimiter.class).debug(
                "Redis TokenBucket result for key: {} => rawResult: {}, allowed: {}", 
                redisKey, result, allowed);

        // 1 means allowed, 0 means blocked
        return allowed;
    }
}
