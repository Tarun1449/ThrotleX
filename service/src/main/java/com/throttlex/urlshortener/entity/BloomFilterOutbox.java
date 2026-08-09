package com.throttlex.urlshortener.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "bloom_filter_outbox",
        indexes = {
                @Index(name = "idx_outbox_scan", columnList = "processed, id, short_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BloomFilterOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BloomFilterOutbox(String shortCode) {
        this.shortCode = shortCode;
        this.processed = false;
        this.createdAt = LocalDateTime.now();
    }
}
