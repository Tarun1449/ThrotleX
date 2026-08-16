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
        int refillRate = DEFAULT_REFILL_RATE;
        
        if (config != null) {
            capacity = config.getLimitCapacity();
            // Refill rate is tokens per second. We calculate it based on windowSeconds.
            // E.g., 100 capacity over 60 seconds = ~1.6 tokens per second. 
            // We'll simplify and require integers for now, or just use capacity/windowSeconds.
            refillRate = Math.max(1, config.getLimitCapacity() / config.getWindowSeconds());
        }
        
        // Args: [capacity, refill_rate, current_timestamp_in_seconds]
        long currentTimestamp = Instant.now().getEpochSecond();
        
        Long result = redisTemplate.execute(
                redisScript,
                keys,
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(currentTimestamp)
        );

        // 1 means allowed, 0 means blocked
        return result != null && result == 1L;
    }
}
