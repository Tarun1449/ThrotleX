package com.throttlex.urlshortener.ratelimit;

import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import com.throttlex.ratelimit.entity.RateLimitConfig;
import com.throttlex.ratelimit.service.RateLimitConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    
    private final RateLimiterFactory factory;
    private final RateLimitConfigService configService;

    public RateLimitService(RateLimiterFactory factory, RateLimitConfigService configService) {
        this.factory = factory;
        this.configService = configService;
    }

    /**
     * Checks if a request by the given IP for a shortCode is allowed.
     * Fallback to ALLOW (Fail-Open) if Redis is down or throwing exceptions.
     */
    public boolean isAllowed(String shortCode, String clientIp) {
        try {
            RateLimitConfig config = configService.getConfigByShortCode(shortCode);
            RateLimitAlgorithm algorithm = config != null ? config.getAlgorithm() : RateLimitAlgorithm.TOKEN_BUCKET;
            RateLimitStrategy strategy = factory.getStrategy(algorithm);
            return strategy.tryAcquire(shortCode, clientIp);
        } catch (Exception e) {
            log.error("Rate limiter failure for code {} and IP {}. Failing open (allowing request). Error: {}", shortCode, clientIp, e.getMessage());
            return true;
        }
    }
}
