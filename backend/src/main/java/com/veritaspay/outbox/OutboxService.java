package com.veritaspay.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String paymentEventsTopic;

    public OutboxService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.payment-events:veritas.payment.events}") String paymentEventsTopic) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Transactional
    public OutboxEvent enqueuePaymentEvent(String aggregateId, String eventType, Object eventPayload) {
        try {
            String json = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent event = new OutboxEvent("JournalTransaction", aggregateId, eventType, paymentEventsTopic, json);
            return outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<OutboxEvent> listRecentEvents(Pageable pageable) {
        return outboxRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
