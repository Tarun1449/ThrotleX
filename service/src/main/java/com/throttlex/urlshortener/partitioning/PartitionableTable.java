package com.throttlex.urlshortener.partitioning;

import lombok.Getter;

@Getter
public enum PartitionableTable {
    URLS("urls"),
    BLOOM_FILTER_OUTBOX("bloom_filter_outbox");

    private final String tableName;

    PartitionableTable(String tableName) {
        this.tableName = tableName;
    }
}
