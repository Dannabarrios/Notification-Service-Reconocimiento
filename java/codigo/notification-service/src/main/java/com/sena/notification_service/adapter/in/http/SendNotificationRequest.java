package com.sena.notification_service.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public record SendNotificationRequest(
        @JsonProperty("recipient_id") UUID recipientId,
        @JsonProperty("recipient_email") String recipientEmail,
        @JsonProperty("channel") String channel,
        @JsonProperty("subject") String subject,
        @JsonProperty("template_code") String templateCode,
        @JsonProperty("template_vars") Map<String, String> templateVars,
        @JsonProperty("source_service") String sourceService,
        @JsonProperty("source_event_id") UUID sourceEventId) {
}
