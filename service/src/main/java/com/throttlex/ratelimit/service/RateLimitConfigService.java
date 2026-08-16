package com.throttlex.ratelimit.service;

import com.throttlex.ratelimit.entity.RateLimitConfig;
import com.throttlex.ratelimit.repository.RateLimitConfigRepository;
import com.throttlex.urlshortener.entity.Url;
import com.throttlex.urlshortener.repository.UrlRepository;
import com.throttlex.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    private final RateLimitConfigRepository rateLimitConfigRepository;
    private final UrlRepository urlRepository;

    /**
     * Fetches the rate limit config for a given shortCode.
     * Heavily cached in Redis to prevent DB hammering on every redirect.
     */
    @Cacheable(value = "rate_limit_configs", key = "#shortCode", unless = "#result == null")
    public RateLimitConfig getConfigByShortCode(String shortCode) {
        long urlId = Base62Encoder.decode(shortCode);
        return rateLimitConfigRepository.findByUrlId(urlId).orElse(null);
    }

    /**
     * Saves or updates the rate limit configuration and immediately updates the cache.
     */
    @Transactional
    @CachePut(value = "rate_limit_configs", key = "#shortCode")
    public RateLimitConfig saveOrUpdateConfig(String shortCode, RateLimitConfig config) {
        long urlId = Base62Encoder.decode(shortCode);
        
        int rowsUpdated = rateLimitConfigRepository.updateConfigByUrlId(
                urlId, config.getAlgorithm(), config.getLimitCapacity(), config.getWindowSeconds());
                
        if (rowsUpdated > 0) {
            // The record existed and was successfully updated via direct SQL.
            // We must return the 'config' object so @CachePut can save it in Redis!
            return config;
        } else {
            // No rows updated means the configuration didn't exist yet (first time).
            // We must insert it. This requires the Url entity reference for the foreign key.
            Url url = urlRepository.findById(urlId)
                    .orElseThrow(() -> new IllegalArgumentException("URL not found for shortCode: " + shortCode));
            config.setUrl(url);
            return rateLimitConfigRepository.save(config);
        }
    }
}
