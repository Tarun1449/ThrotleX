package com.throttlex.urlshortener.partitioning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartitionCreationEvent {
    private PartitionableTable table;
    private int year;
    private int month;
}
