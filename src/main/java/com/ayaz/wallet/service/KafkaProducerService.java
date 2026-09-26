package com.ayaz.wallet.service;

import com.ayaz.wallet.dto.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "wallet.transactions";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public void publishTransactionEvent(TransactionEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getUserId(), json);
            log.info("Published transaction event to Kafka: type={} userId={} amount={}",
                    event.getType(), event.getUserId(), event.getAmount());
        } catch (Exception e) {
            // Never fail the main transaction because of Kafka
            log.error("Failed to publish Kafka event: {}", e.getMessage());
        }
    }
}