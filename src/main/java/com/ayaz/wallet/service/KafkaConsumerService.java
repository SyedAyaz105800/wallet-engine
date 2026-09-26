package com.ayaz.wallet.service;

import com.ayaz.wallet.dto.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaConsumerService {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @KafkaListener(
        topics = "wallet.transactions",
        groupId = "wallet-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            log.info("===== KAFKA EVENT RECEIVED =====");
            log.info("Partition: {} | Offset: {}", partition, offset);
            log.info("Event: {} | User: {} | Amount: {} | Balance: {} -> {}",
                    event.getType(),
                    event.getUserId(),
                    event.getAmount(),
                    event.getBalanceBefore(),
                    event.getBalanceAfter());
            log.info("================================");

            // In real system: trigger notification, update analytics, write to audit log
            processDownstreamActions(event);

        } catch (Exception e) {
            log.error("Failed to process Kafka event: {}", e.getMessage());
        }
    }

    private void processDownstreamActions(TransactionEvent event) {
        switch (event.getType()) {
            case DEPOSIT -> log.info("DOWNSTREAM: Notify user {} of deposit Rs.{}",
                    event.getUserId(), event.getAmount());
            case WITHDRAWAL -> log.info("DOWNSTREAM: Update analytics for withdrawal Rs.{}",
                    event.getAmount());
            case ROLLBACK -> log.info("DOWNSTREAM: Alert risk team of rollback for user {}",
                    event.getUserId());
        }
    }
}