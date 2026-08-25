package com.sena.notification_service.domain.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationTemplate(
        UUID id,
        String code,
        Channel channel,
        String subjectTemplate,
        String bodyTemplate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
