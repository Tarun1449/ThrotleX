package com.throttlex.urlshortener.partitioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionScheduler {

    private final PartitionKafkaProducer partitionKafkaProducer;

    // Run on startup and every 3 hours
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    public void schedulePartitionChecks() {
        log.info("Starting automated database partition scheduling checks...");
        
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalDate nextMonth = today.plusMonths(1);

        for (PartitionableTable table : PartitionableTable.values()) {
            // Check current month
            partitionKafkaProducer.publishPartitionEvent(PartitionCreationEvent.builder()
                    .table(table)
                    .year(today.getYear())
                    .month(today.getMonthValue())
                    .build());

            // Check next month (to ensure partitions are created before the month rolls over)
            partitionKafkaProducer.publishPartitionEvent(PartitionCreationEvent.builder()
                    .table(table)
                    .year(nextMonth.getYear())
                    .month(nextMonth.getMonthValue())
                    .build());
        }
    }
}
