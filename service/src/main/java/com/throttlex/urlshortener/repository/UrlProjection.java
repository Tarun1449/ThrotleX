package com.throttlex.urlshortener.repository;

import java.time.Instant;

public interface UrlProjection {
    String getOriginalUrl();
    Instant getExpiresAt();
}
