package com.throttlex.urlshortener.ratelimit;

import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateLimiterFactory {

    private final Map<RateLimitAlgorithm, RateLimitStrategy> strategies;

    public RateLimiterFactory(TokenBucketRateLimiter tokenBucketRateLimiter) {
        // Map enum to the correct implementation. 
        // As we add more algorithms, we inject them here.
        this.strategies = Map.of(
                RateLimitAlgorithm.TOKEN_BUCKET, tokenBucketRateLimiter
        );
    }

    public RateLimitStrategy getStrategy(RateLimitAlgorithm algorithm) {
        RateLimitStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            throw new IllegalArgumentException("Rate limiting algorithm not supported: " + algorithm);
        }
        return strategy;
    }
}
