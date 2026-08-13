package com.throttlex.config.web;

import com.throttlex.urlshortener.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply the rate limiter strictly to the core URL creation API endpoints.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/urls/**");
    }
}
