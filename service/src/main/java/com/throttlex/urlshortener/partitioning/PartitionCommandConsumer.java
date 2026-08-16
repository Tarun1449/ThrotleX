package com.throttlex.urlshortener.partitioning;

import com.throttlex.urlshortener.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionCommandConsumer {

    private final JdbcTemplate jdbcTemplate;

    @KafkaListener(topics = "db-partition-commands", groupId = "partitioning-group")
    public void consumePartitionEvent(PartitionCreationEvent event) {
        log.info("Received partition creation request: Table={}, Year={}, Month={}", 
            event.getTable().getTableName(), event.getYear(), event.getMonth());
            
        try {
            createPartitionIfNotExists(event.getTable().getTableName(), event.getYear(), event.getMonth());
            
            // Periodically ensure the Outbox partial index exists (background execution instead of app startup)
            ensureOutboxPartialIndexExists();
        } catch (Exception e) {
            log.error("Failed to execute partition creation for table {}", event.getTable().getTableName(), e);
        }
    }

    private void ensureOutboxPartialIndexExists() {
        String sql = "CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed " +
                     "ON bloom_filter_outbox (id, short_code) " +
                     "WHERE processed = false";
        jdbcTemplate.execute(sql);
    }

    private void createPartitionIfNotExists(String parentTable, int year, int month) {
        // Calculate the boundary timestamps
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate startOfNextMonth = startOfMonth.plusMonths(1);

        long startMillis = startOfMonth.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long endMillis = startOfNextMonth.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

        // Convert timestamps to Snowflake ID bounds
        long startSnowflakeId = SnowflakeIdGenerator.getLowerBoundForTimestamp(startMillis);
        long endSnowflakeId = SnowflakeIdGenerator.getLowerBoundForTimestamp(endMillis);

        // Format partition name, e.g., url_clicks_2026_08
        String monthString = String.format("%02d", month);
        String partitionName = String.format("%s_%d_%s", parentTable, year, monthString);

        String sql = String.format(
            "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM (%d) TO (%d)",
            partitionName, parentTable, startSnowflakeId, endSnowflakeId
        );

        log.info("Executing DDL: {}", sql);
        jdbcTemplate.execute(sql);
        log.info("Successfully ensured partition {} exists.", partitionName);
    }
}
