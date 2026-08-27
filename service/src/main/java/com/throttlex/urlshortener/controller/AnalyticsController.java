package com.throttlex.urlshortener.controller;

import com.throttlex.urlshortener.repository.ClickHouseAnalyticsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final ClickHouseAnalyticsRepository analyticsRepository;

    public AnalyticsController(ClickHouseAnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestParam(defaultValue = "ALL") String shortCode,
            @RequestParam(defaultValue = "7d") String timeRange
    ) {
        Map<String, Object> data = analyticsRepository.getAnalyticsSummary(shortCode, timeRange);
        return ResponseEntity.ok(data);
    }
}
