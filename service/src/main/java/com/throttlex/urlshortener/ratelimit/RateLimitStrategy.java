package com.throttlex.urlshortener.ratelimit;

public interface RateLimitStrategy {
    
    /**
     * Determines whether the requested action is allowed under the current rate limit.
     * 
     * @param shortCode The short URL being accessed.
     * @param clientIp The IP address of the client making the request.
     * @return true if the request is allowed, false if rate limited.
     */
    boolean tryAcquire(String shortCode, String clientIp);
}
