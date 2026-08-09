package com.throttlex.urlshortener.service;

import com.throttlex.urlshortener.dto.BloomFilterSyncEvent;
import com.throttlex.urlshortener.entity.BloomFilterOutbox;
import com.throttlex.urlshortener.kafka.KafkaGroupConstants;
import com.throttlex.urlshortener.kafka.KafkaTopicConstants;
import com.throttlex.urlshortener.repository.BloomFilterOutboxProjection;
import com.throttlex.urlshortener.repository.BloomFilterOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.kafka.annotation.KafkaListener;
import com.throttlex.urlshortener.kafka.BloomFilterKafkaPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BloomFilterSyncConsumer {

    private final BloomFilterOutboxRepository outboxRepository;
    private final RBloomFilter<String> urlBloomFilter;
    private final BloomFilterKafkaPublisher kafkaPublisher;
    private final BloomFilterWarmupService warmupService;

    public BloomFilterSyncConsumer(BloomFilterOutboxRepository outboxRepository,
                                   RBloomFilter<String> urlBloomFilter,
                                   BloomFilterKafkaPublisher kafkaPublisher,
                                   BloomFilterWarmupService warmupService) {
        this.outboxRepository = outboxRepository;
        this.urlBloomFilter = urlBloomFilter;
        this.kafkaPublisher = kafkaPublisher;
        this.warmupService = warmupService;
    }

    /**
     * The heart of the Kafka Chaining architecture.
     * Consumes a cursor, processes a batch safely, and fires the next cursor.
     */
    @KafkaListener(
            topics = KafkaTopicConstants.BLOOM_FILTER_SYNC,
            groupId = KafkaGroupConstants.BLOOM_FILTER_SYNC_GROUP,
            containerFactory = "bloomFilterContainerFactory"
    )
    @Transactional
    public void consumeSyncEvent(BloomFilterSyncEvent event) {

        // 1. Fetch exactly 1000 using Index-Only Scan on processed=false
        List<BloomFilterOutboxProjection> batch = outboxRepository.findTop1000ByProcessedFalseOrderByIdAsc();

        // 2. Base Case: If the batch is empty, we reached the end of the queue!
        if (batch.isEmpty()) {
            log.info("Kafka Warmup Pipeline complete! Outbox fully flushed.");
            warmupService.setWarmup(false); // Turn Bloom Filter protection back ON!
            return;
        }

        // 3. Process the batch (Add to Redis Bloom Filter)
        log.info("Processing Outbox Batch: {} items", batch.size());
        List<Long> idsToMark = batch.stream().map(outbox -> {
            urlBloomFilter.add(outbox.getShortCode());
            return outbox.getId();
        }).collect(Collectors.toList());

        // 4. Mark as processed in Postgres
        outboxRepository.markAsProcessed(idsToMark);

        // 5. Fire the next link in the chain!
        kafkaPublisher.publishSyncEvent(new BloomFilterSyncEvent());
    }
}
