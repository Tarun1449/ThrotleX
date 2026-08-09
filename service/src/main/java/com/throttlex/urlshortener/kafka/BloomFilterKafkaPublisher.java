package com.throttlex.urlshortener.kafka;

import com.throttlex.urlshortener.dto.BloomFilterSyncEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Dedicated publisher class to abstract the KafkaTemplate dependency
 * and enforce the correct Topic constants for Bloom Filter synchronization.
 */
@Component
public class BloomFilterKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BloomFilterKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishSyncEvent(BloomFilterSyncEvent event) {
        kafkaTemplate.send(KafkaTopicConstants.BLOOM_FILTER_SYNC, event);
    }
}
