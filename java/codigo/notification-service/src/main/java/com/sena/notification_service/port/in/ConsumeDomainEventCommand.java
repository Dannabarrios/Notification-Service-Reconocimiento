package com.sena.notification_service.port.in;

import java.util.Map;

public record ConsumeDomainEventCommand(
        String eventId,
        String eventType,
        String sourceService,
        Map<String, Object> payload,
        String traceParent) {
}
