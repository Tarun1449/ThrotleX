package com.throttlex.ratelimit.dto;

import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RateLimitConfigRequest {
    @NotNull(message = "Algorithm is required")
    private RateLimitAlgorithm algorithm;

    @NotNull(message = "Limit capacity is required")
    @Min(value = 1, message = "Limit capacity must be at least 1")
    private Integer limitCapacity;

    @NotNull(message = "Window seconds is required")
    @Min(value = 1, message = "Window seconds must be at least 1")
    private Integer windowSeconds;
}
