package com.throttlex.ratelimit.entity;

import com.throttlex.common.entity.BaseEntity;
import com.throttlex.urlshortener.entity.Url;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rate_limit_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class RateLimitConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Url url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RateLimitAlgorithm algorithm;

    @Column(name = "limit_capacity", nullable = false)
    private Integer limitCapacity;

    @Column(name = "window_seconds", nullable = false)
    private Integer windowSeconds;
}
