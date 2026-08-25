package com.sena.notification_service.port.in;

import com.sena.notification_service.domain.model.Channel;

import java.util.Map;
import java.util.UUID;

public record SendNotificationCommand(
        UUID recipientId,
        String recipientEmail,
        Channel channel,
        String subject,
        String templateCode,
        Map<String, String> templateVars,
        String sourceService,
        UUID sourceEventId) {
}
