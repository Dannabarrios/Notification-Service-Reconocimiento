package com.sena.notification_service.adapter.out.messaging;

import com.sena.notification_service.config.RabbitTopologyConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("worker")
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;
    private final Propagator propagator;
    private final int batchSize;

    public OutboxRelay(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            RabbitTemplate rabbitTemplate,
            JsonMapper jsonMapper,
            Tracer tracer,
            Propagator propagator,
            @Value("${notification.outbox.batch-size:20}") int batchSize) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
        this.propagator = propagator;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-interval-ms:2000}")
    public void relay() {
        try {
            transactionTemplate.executeWithoutResult(status -> publishBatch());
        } catch (RuntimeException ex) {
            log.error("outbox relay error", ex);
        }
    }

    private void publishBatch() {
        String select = """
                SELECT id, event_id, event_type, payload::text, created_at
                FROM notification.outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;
        List<StagedEvent> events = jdbc.query(select, (rs, rowNum) -> new StagedEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant()), batchSize);

        for (StagedEvent event : events) {
            publish(event);
            jdbc.update("UPDATE notification.outbox SET published_at = now() WHERE id = ?::uuid", event.id().toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void publish(StagedEvent event) {
        Span span = null;
        try {
            Map<String, Object> payload = jsonMapper.readValue(event.payloadJson(), Map.class);
            Map<String, String> parentCarrier = new HashMap<>();
            Object traceParent = payload.get("trace_parent");
            if (traceParent instanceof String trace && !trace.isBlank()) {
                parentCarrier.put("traceparent", trace);
            }

            span = propagator.extract(parentCarrier, Map::get)
                    .name("outbox.publish " + event.eventType())
                    .kind(Span.Kind.PRODUCER)
                    .tag("messaging.system", "rabbitmq")
                    .tag("messaging.destination", RabbitTopologyConfiguration.NOTIFICATION_EXCHANGE)
                    .tag("event.type", event.eventType())
                    .tag("event.id", event.eventId().toString())
                    .start();

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event_id", event.eventId().toString());
            envelope.put("event_type", event.eventType());
            envelope.put("version", "1.0");
            envelope.put("timestamp", event.createdAt());
            envelope.put("source_service", "notification-service");
            envelope.put("payload", payload);

            MessageBuilderSupport<Message> builder = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.eventId().toString());
            Map<String, String> outgoingCarrier = new HashMap<>();
            propagator.inject(span.context(), outgoingCarrier, Map::put);
            outgoingCarrier.forEach(builder::setHeader);

            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                Message message = builder.build();
                rabbitTemplate.send(RabbitTopologyConfiguration.NOTIFICATION_EXCHANGE, event.eventType(), message);
            }
        } catch (Exception ex) {
            if (span != null) {
                span.error(ex);
            }
            throw new IllegalStateException("publish outbox event " + event.eventId(), ex);
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    private record StagedEvent(UUID id, UUID eventId, String eventType, String payloadJson, Instant createdAt) {}
}
