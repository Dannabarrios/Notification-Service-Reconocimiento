package com.sena.notification_service.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID eventId,
        String eventType,
        Map<String, Object> payload,
        Instant createdAt) {
}
