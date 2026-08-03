package com.throttlex.urlshortener.entity;

import com.throttlex.common.entity.BaseEntity;
import com.throttlex.ratelimit.entity.RateLimitConfig;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "urls", indexes = {
        @Index(name = "idx_url_short_code", columnList = "short_code", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Url extends BaseEntity {

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToOne(mappedBy = "url", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    private RateLimitConfig rateLimitConfig;
}
