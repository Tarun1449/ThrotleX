package com.throttlex.urlshortener.ratelimit;

public interface RateLimitStrategy {
    
    /**
     * Determines whether the requested action is allowed under the current rate limit.
     * 
     * @param key The unique identifier for the user (e.g., IP address or API key).
     * @return true if the request is allowed, false if rate limited.
     */
    boolean tryAcquire(String key);
}
