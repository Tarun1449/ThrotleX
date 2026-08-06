package com.throttlex.urlshortener.service;

import com.throttlex.urlshortener.dto.CreateUrlRequest;
import com.throttlex.urlshortener.dto.UrlCacheDto;
import com.throttlex.urlshortener.dto.UrlResponse;
import com.throttlex.urlshortener.entity.Url;
import com.throttlex.urlshortener.repository.UrlProjection;
import com.throttlex.urlshortener.repository.UrlRepository;
import com.throttlex.urlshortener.util.Base62Encoder;
import com.throttlex.urlshortener.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    
    // Inject self to bypass Spring AOP proxy limitations for internal method calls
    @Autowired
    @Lazy
    private UrlShortenerService self;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

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

        urlRepository.save(url);
        
        // Write-Through to Redis Cache instantly!
        self.pushToCache(shortCode, new UrlCacheDto(url.getOriginalUrl(), url.getExpiresAt()));

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
        // Fetch from Redis OR Postgres (Read-Through Cache)
        UrlCacheDto cachedUrl = self.getCachedUrl(shortCode);

        // Validate expiration here so even cached items are correctly validated!
        if (cachedUrl.expiresAt() != null && cachedUrl.expiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("URL has expired");
        }

        return cachedUrl.originalUrl();
    }
    
    @Cacheable(value = "urls", key = "#shortCode", sync = true)
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
                .orElseThrow(() -> new RuntimeException("URL not found"));

        // Return the clean, serializable DTO for Redis to save
        return new UrlCacheDto(url.getOriginalUrl(), url.getExpiresAt());
    }
}
