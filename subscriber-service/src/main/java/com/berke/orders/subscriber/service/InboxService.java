package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.repo.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboxService {
    private final ProcessedEventRepository repository;
    private final ObjectMapper objectMapper;

    public boolean begin(String consumer, UUID eventId, String eventType, int eventVersion, UUID correlationId) {
        return repository.insertIfAbsent(consumer, eventId, eventType, eventVersion, correlationId) == 1;
    }

    public void storeResult(String consumer, UUID eventId, Object result) {
        try {
            if (repository.storeResult(consumer, eventId, objectMapper.writeValueAsString(result)) != 1) {
                throw new IllegalStateException("Processed event disappeared: " + eventId);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize processed event result", e);
        }
    }

    public <T> T replay(String consumer, UUID eventId, Class<T> type) {
        var row = repository.findByConsumerNameAndEventId(consumer, eventId).orElseThrow();
        if (row.getResultJson() == null) throw new IllegalStateException("Processed event has no stored result: " + eventId);
        try {
            return objectMapper.readValue(row.getResultJson(), type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize processed event result", e);
        }
    }
}
