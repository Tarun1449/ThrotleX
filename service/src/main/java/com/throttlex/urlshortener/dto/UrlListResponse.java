package com.throttlex.urlshortener.dto;

import java.time.Instant;

public record UrlListResponse(
        Long id,
        String shortCode,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        Long clicks,
        String rateLimitAlgorithm,
        Integer rateLimitCapacity,
        Integer rateLimitWindowSeconds
) {
}
