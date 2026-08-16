package com.throttlex.ratelimit.dto;

import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfigResponse {
    private String shortCode;
    private RateLimitAlgorithm algorithm;
    private Integer limitCapacity;
    private Integer windowSeconds;
}
