package com.throttlex.urlshortener.partitioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "db-partition-commands";

    public void publishPartitionEvent(PartitionCreationEvent event) {
        log.info("Publishing partition creation event for table: {}, year: {}, month: {}", 
            event.getTable().getTableName(), event.getYear(), event.getMonth());
        
        kafkaTemplate.send(TOPIC, event);
    }
}
