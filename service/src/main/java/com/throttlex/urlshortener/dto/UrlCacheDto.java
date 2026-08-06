package com.throttlex.urlshortener.dto;

import java.time.Instant;

/**
 * A lightweight, JSON-serializable DTO for Redis caching.
 * Storing both the URL and Expiry allows the read-through cache
 * to validate expirations without hitting PostgreSQL.
 */
public record UrlCacheDto(String originalUrl, Instant expiresAt) {}
