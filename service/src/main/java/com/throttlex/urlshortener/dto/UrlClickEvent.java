package com.throttlex.urlshortener.dto;

import java.time.Instant;
import java.util.UUID;

public record UrlClickEvent(
    String id,
    String shortCode,
    String originalUrl,
    String ipAddress,
    String userAgent,
    String referer,
    String countryCode,
    String deviceType,
    String browser,
    int statusCode,
    String declineReason,
    boolean isBot,
    String createdAt
) {
    public static UrlClickEvent success(
            String shortCode, 
            String originalUrl, 
            String ip, 
            String userAgent, 
            String referer, 
            String country, 
            String device, 
            String browser
    ) {
        return new UrlClickEvent(
            UUID.randomUUID().toString(),
            shortCode,
            originalUrl != null ? originalUrl : "",
            ip != null ? ip : "127.0.0.1",
            userAgent != null ? userAgent : "Unknown",
            referer != null ? referer : "Direct",
            country != null ? country : "US",
            device != null ? device : "Desktop",
            browser != null ? browser : "Chrome",
            302,
            "NONE",
            false,
            Instant.now().toString()
        );
    }

    public static UrlClickEvent declined(
            String shortCode, 
            String ip, 
            String userAgent, 
            String referer, 
            int statusCode, 
            String declineReason
    ) {
        return new UrlClickEvent(
            UUID.randomUUID().toString(),
            shortCode != null ? shortCode : "UNKNOWN",
            "",
            ip != null ? ip : "127.0.0.1",
            userAgent != null ? userAgent : "Unknown",
            referer != null ? referer : "Direct",
            "UNKNOWN",
            "Unknown",
            "Unknown",
            statusCode,
            declineReason != null ? declineReason : "UNKNOWN",
            "BOT_BLOCKED".equals(declineReason),
            Instant.now().toString()
        );
    }
}
