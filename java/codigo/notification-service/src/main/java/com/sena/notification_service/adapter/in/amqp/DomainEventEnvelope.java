package com.sena.notification_service.adapter.in.amqp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record DomainEventEnvelope(
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("payload") Map<String, Object> payload,
        @JsonProperty("source_service") String sourceService,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("version") String version) {
}
