package com.throttlex.ratelimit.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RateLimitConfig extends BaseEntity {

    @JsonIgnore
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
