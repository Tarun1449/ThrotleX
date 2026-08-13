package com.throttlex.urlshortener.ratelimit;

public enum RateLimitAlgorithm {
    TOKEN_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG
}
