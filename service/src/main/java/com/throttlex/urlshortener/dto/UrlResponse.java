package com.throttlex.urlshortener.dto;

import java.time.Instant;

public record UrlResponse(
        String shortCode,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt
) {}
