package com.throttlex.urlshortener.service;

import com.throttlex.urlshortener.dto.CreateUrlRequest;
import com.throttlex.urlshortener.dto.UrlCacheDto;
import com.throttlex.urlshortener.dto.UrlListResponse;
import com.throttlex.urlshortener.dto.UrlResponse;
import com.throttlex.urlshortener.entity.Url;
import com.throttlex.urlshortener.repository.BloomFilterOutboxRepository;
import com.throttlex.urlshortener.repository.UrlProjection;
import com.throttlex.urlshortener.repository.UrlRepository;
import com.throttlex.urlshortener.util.Base62Encoder;
import com.throttlex.urlshortener.util.SnowflakeIdGenerator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.throttlex.common.exception.UrlNotFoundException;
import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import com.throttlex.ratelimit.entity.RateLimitConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RBloomFilter<String> urlBloomFilter;
    private final CircuitBreaker circuitBreaker;
    private final BloomFilterWarmupService warmupService;
    private final BloomFilterOutboxRepository outboxRepository;
    private final com.throttlex.ratelimit.service.RateLimitConfigService rateLimitConfigService;

    @Autowired
    @Lazy
    private UrlShortenerService self;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public UrlShortenerService(UrlRepository urlRepository,
                               SnowflakeIdGenerator snowflakeIdGenerator,
                               RBloomFilter<String> urlBloomFilter,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               BloomFilterWarmupService warmupService,
                               BloomFilterOutboxRepository outboxRepository,
                               com.throttlex.ratelimit.service.RateLimitConfigService rateLimitConfigService) {
        this.urlRepository = urlRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.urlBloomFilter = urlBloomFilter;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");
        this.warmupService = warmupService;
        this.outboxRepository = outboxRepository;
        this.rateLimitConfigService = rateLimitConfigService;
    }

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        long id = snowflakeIdGenerator.nextId();
        String shortCode = Base62Encoder.encode(id);

        Instant expiresAt = null;
        if (request.expiryDays() != null) {
            expiresAt = Instant.now().plus(request.expiryDays(), ChronoUnit.DAYS);
        }

        Url url = Url.builder()
                .shortCode(shortCode)
                .originalUrl(request.originalUrl())
                .expiresAt(expiresAt)
                .build();
        url.setId(id); // Set the Snowflake ID manually
        
        RateLimitAlgorithm algo = request.rateLimitAlgorithm() != null ? RateLimitAlgorithm.valueOf(request.rateLimitAlgorithm()) : RateLimitAlgorithm.TOKEN_BUCKET;
        int capacity = request.rateLimitCapacity() != null ? request.rateLimitCapacity() : 40;
        int window = request.rateLimitWindowSeconds() != null ? request.rateLimitWindowSeconds() : 40;

        RateLimitConfig rateLimitConfig = RateLimitConfig.builder()
                .url(url)
                .algorithm(algo)
                .limitCapacity(capacity)
                .windowSeconds(window)
                .build();
        rateLimitConfig.setId(snowflakeIdGenerator.nextId());
        url.setRateLimitConfig(rateLimitConfig);

        urlRepository.save(url);
        
        // Add to Bloom Filter (Fail-Safe: If Redis is full or null, save to Outbox)
        if (urlBloomFilter != null) {
            try {
                circuitBreaker.executeRunnable(() -> urlBloomFilter.add(shortCode));
            } catch (Exception e) {
                log.warn("Bloom filter add failed (Redis down?), saving to Outbox. ShortCode: {}", shortCode);
                outboxRepository.save(new com.throttlex.urlshortener.entity.BloomFilterOutbox(snowflakeIdGenerator.nextId(), shortCode));
            }
        } else {
            outboxRepository.save(new com.throttlex.urlshortener.entity.BloomFilterOutbox(snowflakeIdGenerator.nextId(), shortCode));
        }
        
        // Write-Through to Redis Cache instantly!
        self.pushToCache(shortCode, new UrlCacheDto(url.getOriginalUrl(), url.getExpiresAt()));
        if (url.getRateLimitConfig() != null) {
            try {
                rateLimitConfigService.saveOrUpdateConfig(shortCode, url.getRateLimitConfig());
            } catch (Exception e) {
                log.warn("Failed to write-through rate limit config to cache for shortCode: {}", shortCode, e);
            }
        }

        return new UrlResponse(
                url.getShortCode(),
                url.getOriginalUrl(),
                Instant.now(), // Estimate since it hasn't flushed yet
                url.getExpiresAt()
        );
    }
    
    @CachePut(value = "urls", key = "#shortCode")
    public UrlCacheDto pushToCache(String shortCode, UrlCacheDto dto) {
        return dto; // The return value is what Spring saves into Redis
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortCode) {
        // 1. Check Bloom Filter first (O(1) time complexity)
        boolean mightExist = true; // Fail-Open: Assume it exists if Redis crashes

        // If Redis is actively down, OR urlBloomFilter is null, OR we are syncing the Outbox via Kafka, bypass the Bloom Filter
        if (urlBloomFilter == null || circuitBreaker.getState() != CircuitBreaker.State.CLOSED || warmupService.isWarmup()) {
            mightExist = true; 
        } else {
            try {
                mightExist = circuitBreaker.executeSupplier(() -> urlBloomFilter.contains(shortCode));
            } catch (Exception e) {
                // Redis is down! We must not crash the API. 
                // We bypass the Bloom Filter and fallback to standard caching/DB behavior.
                mightExist = true;
            }
        }

        // If it returns false, the shortCode DEFINITELY does not exist. Stop immediately!
        if (!mightExist) {
            Metrics.counter("throttlex.bloom.filter.rejected").increment();
            log.debug("Bloom Filter check returned false for shortCode: {}", shortCode);
            throw new UrlNotFoundException("URL not found");
        }
        
        // 2. Fetch from Redis OR Postgres (Read-Through Cache)
        UrlCacheDto cachedUrl = self.getCachedUrl(shortCode);

        // Validate expiration here so even cached items are correctly validated!
        if (cachedUrl.expiresAt() != null && cachedUrl.expiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("URL has expired");
        }

        return cachedUrl.originalUrl();
    }
    
    @Cacheable(value = "urls", key = "#shortCode")
    public UrlCacheDto getCachedUrl(String shortCode) {
        long id = Base62Encoder.decode(shortCode);
        
        // 1. Reverse engineer the exact timestamp from the Snowflake ID
        long timestampMillis = SnowflakeIdGenerator.extractTimestamp(id);
        Instant creationInstant = Instant.ofEpochMilli(timestampMillis);

        // 2. Convert to IST to find the exact Monthly Partition boundaries
        ZonedDateTime zdt = creationInstant.atZone(IST_ZONE);
        ZonedDateTime startOfMonth = zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endOfMonth = startOfMonth.plusMonths(1);

        // 3. Query PostgreSQL passing the ID and the Month Boundaries to force Partition Pruning
        UrlProjection url = urlRepository.findByIdAndCreatedAtBetween(id, startOfMonth.toInstant(), endOfMonth.toInstant())
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // Return the clean, serializable DTO for Redis to save
        return new UrlCacheDto(url.getOriginalUrl(), url.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<UrlListResponse> getUrls(Long cursorId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<Url> urls;

        if (cursorId == null) {
            urls = urlRepository.findByOrderByIdDesc(pageRequest);
        } else {
            urls = urlRepository.findByIdLessThanOrderByIdDesc(cursorId, pageRequest);
        }

        return urls.stream().map(url -> new UrlListResponse(
                url.getId(),
                url.getShortCode(),
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                0L, // Placeholder for clicks
                url.getRateLimitConfig() != null ? url.getRateLimitConfig().getAlgorithm().name() : null,
                url.getRateLimitConfig() != null ? url.getRateLimitConfig().getLimitCapacity() : null,
                url.getRateLimitConfig() != null ? url.getRateLimitConfig().getWindowSeconds() : null
        )).collect(Collectors.toList());
    }
}
