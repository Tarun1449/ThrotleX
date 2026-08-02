package com.throttlex.urlshortener.controller;

import com.throttlex.urlshortener.dto.CreateUrlRequest;
import com.throttlex.urlshortener.dto.UrlResponse;
import com.throttlex.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlShortenerService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToOriginal(@PathVariable String shortCode) {
        String originalUrl = urlShortenerService.getOriginalUrl(shortCode);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));
        
        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT); // 307 Redirect
    }
}
