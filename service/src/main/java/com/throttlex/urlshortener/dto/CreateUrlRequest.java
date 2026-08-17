package com.throttlex.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateUrlRequest(
        @NotBlank(message = "Original URL cannot be blank")
        @URL(message = "Must be a valid URL")
        String originalUrl,
        
        Integer expiryDays,
        
        String rateLimitAlgorithm,
        Integer rateLimitCapacity,
        Integer rateLimitWindowSeconds
) {}
