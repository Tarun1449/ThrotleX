package com.throttlex.urlshortener.ratelimit;

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

    // Hardcoding limits for now. Can be moved to application.yml later.
    private static final int MAX_TOKENS = 10; // Burst capacity
    private static final int REFILL_RATE = 2; // Tokens per second

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Load the Lua script once during initialization
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.redisScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String key) {
        String redisKey = "ratelimit:token_bucket:" + key;
        
        // Keys: [redisKey]
        List<String> keys = Collections.singletonList(redisKey);
        
        // Args: [capacity, refill_rate, current_timestamp_in_seconds]
        long currentTimestamp = Instant.now().getEpochSecond();
        
        Long result = redisTemplate.execute(
                redisScript,
                keys,
                String.valueOf(MAX_TOKENS),
                String.valueOf(REFILL_RATE),
                String.valueOf(currentTimestamp)
        );

        // 1 means allowed, 0 means blocked
        return result != null && result == 1L;
    }
}
