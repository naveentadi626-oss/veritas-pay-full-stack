package com.veritaspay.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veritaspay.ledger.JournalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Downstream settlement consumer listening on Kafka payment events to transition POSTED -> SETTLED.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final JournalTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(JournalTransactionRepository transactionRepository, ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events:veritas.payment.events}", groupId = "veritas-settlement-group")
    @Transactional
    public void onPaymentEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.hasNonNull("transactionId")) {
                return;
            }
            UUID txId = UUID.fromString(root.get("transactionId").asText());
            transactionRepository.findById(txId).ifPresent(tx -> {
                tx.markSettled();
                transactionRepository.save(tx);
                log.info("Settled transaction {} via Kafka consumer", tx.getReferenceId());
            });
        } catch (Exception ex) {
            log.error("Failed processing payment event from Kafka: {}", ex.getMessage(), ex);
        }
    }
}
