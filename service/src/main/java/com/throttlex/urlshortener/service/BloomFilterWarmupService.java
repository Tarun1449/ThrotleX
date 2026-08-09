package com.throttlex.urlshortener.service;

import com.throttlex.urlshortener.dto.BloomFilterSyncEvent;
import com.throttlex.urlshortener.kafka.BloomFilterKafkaPublisher;
import com.throttlex.urlshortener.repository.BloomFilterOutboxRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class BloomFilterWarmupService {

    private final AtomicBoolean isWarmup = new AtomicBoolean(false);
    
    private final BloomFilterOutboxRepository outboxRepository;
    private final BloomFilterKafkaPublisher kafkaPublisher;
    private final CircuitBreaker circuitBreaker;

    public BloomFilterWarmupService(BloomFilterOutboxRepository outboxRepository,
                                    BloomFilterKafkaPublisher kafkaPublisher,
                                    CircuitBreakerRegistry circuitBreakerRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaPublisher = kafkaPublisher;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");
    }

    /**
     * If true, it means the Outbox is actively being synced via Kafka.
     * All read requests should bypass the Bloom Filter and hit the Database
     * to avoid False Negatives.
     */
    public boolean isWarmup() {
        return isWarmup.get();
    }

    public void setWarmup(boolean state) {
        if (isWarmup.compareAndSet(!state, state)) {
            log.info("Bloom Filter Warmup State changed to: {}", state);
        }
    }

    @PostConstruct
    public void registerCircuitBreakerEvents() {
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();

            if (transition == CircuitBreaker.StateTransition.OPEN_TO_CLOSED || 
                transition == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                log.warn("Redis recovered (Circuit CLOSED). Triggering Bloom Filter Warmup via Kafka.");
                // 2. Trigger the Kafka Chaining pipeline (No DB polling needed!)
                setWarmup(true);
                kafkaPublisher.publishSyncEvent(new BloomFilterSyncEvent());
            }
        });
    }
}
