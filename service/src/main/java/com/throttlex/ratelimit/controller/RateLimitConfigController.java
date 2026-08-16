package com.throttlex.ratelimit.controller;

import com.throttlex.ratelimit.dto.RateLimitConfigRequest;
import com.throttlex.ratelimit.dto.RateLimitConfigResponse;
import com.throttlex.ratelimit.entity.RateLimitConfig;
import com.throttlex.ratelimit.service.RateLimitConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls/{shortCode}/rate-limit")
@RequiredArgsConstructor
public class RateLimitConfigController {

    private final RateLimitConfigService rateLimitConfigService;

    @PostMapping
    public ResponseEntity<RateLimitConfigResponse> setRateLimit(
            @PathVariable String shortCode,
            @Valid @RequestBody RateLimitConfigRequest request) {

        RateLimitConfig config = new RateLimitConfig();
        config.setAlgorithm(request.getAlgorithm());
        config.setLimitCapacity(request.getLimitCapacity());
        config.setWindowSeconds(request.getWindowSeconds());

        RateLimitConfig savedConfig = rateLimitConfigService.saveOrUpdateConfig(shortCode, config);

        RateLimitConfigResponse response = RateLimitConfigResponse.builder()
                .shortCode(shortCode)
                .algorithm(savedConfig.getAlgorithm())
                .limitCapacity(savedConfig.getLimitCapacity())
                .windowSeconds(savedConfig.getWindowSeconds())
                .build();

        return ResponseEntity.ok(response);
    }
}
