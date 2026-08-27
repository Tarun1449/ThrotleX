package com.throttlex.urlshortener.controller;

import com.throttlex.urlshortener.dto.UrlClickEvent;
import com.throttlex.urlshortener.service.KafkaClickProducer;
import com.throttlex.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.throttlex.common.exception.UrlNotFoundException;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    private final UrlShortenerService urlShortenerService;
    private final KafkaClickProducer kafkaClickProducer;

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<Void> redirectToOriginalRoot(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        String countryCode = request.getHeader("CF-IPCountry");
        if (countryCode == null || countryCode.isBlank()) {
            countryCode = request.getHeader("X-Country-Code");
        }
        if (countryCode == null || countryCode.isBlank()) {
            countryCode = "IN";
        }

        try {
            String originalUrl = urlShortenerService.getOriginalUrl(shortCode);
            
            // Asynchronously publish click event to Kafka for ClickHouse ingestion
            kafkaClickProducer.sendClickEvent(
                UrlClickEvent.success(shortCode, originalUrl, ip, userAgent, referer, countryCode, "Desktop", "Chrome")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(originalUrl));
            return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);

        } catch (UrlNotFoundException e) {
            kafkaClickProducer.sendClickEvent(
                UrlClickEvent.declined(shortCode, ip, userAgent, referer, 404, "Bloom Filter 404 (Non-Existent URL)")
            );
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }
}
