package com.throttlex.urlshortener.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    
    private final RateLimiterFactory factory;
    
    // We can default to Token Bucket, but this could also be driven by config
    private final RateLimitAlgorithm currentAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET;

    public RateLimitService(RateLimiterFactory factory) {
        this.factory = factory;
    }

    /**
     * Checks if a request by the given key is allowed.
     * Fallback to ALLOW (Fail-Open) if Redis is down or throwing exceptions.
     */
    public boolean isAllowed(String key) {
        try {
            RateLimitStrategy strategy = factory.getStrategy(currentAlgorithm);
            return strategy.tryAcquire(key);
        } catch (Exception e) {
            log.error("Rate limiter failure for key {}. Failing open (allowing request). Error: {}", key, e.getMessage());
            return true;
        }
    }
}
