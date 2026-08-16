package com.throttlex.urlshortener.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import com.throttlex.common.entity.BaseEntity;

@Entity
@Table(name = "bloom_filter_outbox")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BloomFilterOutbox extends BaseEntity {

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    public BloomFilterOutbox(Long id, String shortCode) {
        this.setId(id);
        this.shortCode = shortCode;
        this.processed = false;
    }
}
