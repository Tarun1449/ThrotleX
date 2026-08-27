package com.throttlex.urlshortener.service;

import com.throttlex.urlshortener.dto.UrlClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KafkaClickProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickProducer.class);
    public static final String CLICK_EVENTS_TOPIC = "throttlex-click-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaClickProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendClickEvent(UrlClickEvent event) {
        try {
            // Partition key = shortCode guarantees ordered ingestion per link
            kafkaTemplate.send(CLICK_EVENTS_TOPIC, event.shortCode(), event);
        } catch (Exception e) {
            log.error("Failed to push click event to Kafka for shortCode: {}", event.shortCode(), e);
        }
    }
}
