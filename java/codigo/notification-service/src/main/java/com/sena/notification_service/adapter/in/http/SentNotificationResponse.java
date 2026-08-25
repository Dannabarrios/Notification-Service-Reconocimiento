package com.sena.notification_service.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SentNotificationResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("recipient_id") UUID recipientId,
        @JsonProperty("channel") Channel channel,
        @JsonProperty("send_status") SendStatus sendStatus,
        @JsonProperty("subject") String subject,
        @JsonProperty("sent_at") Instant sentAt) {
    static SentNotificationResponse from(SentNotification notification) {
        return new SentNotificationResponse(
                notification.getId(),
                notification.getRecipientId(),
                notification.getChannel(),
                notification.getSendStatus(),
                notification.getSubject(),
                notification.getSentAt());
    }
}
