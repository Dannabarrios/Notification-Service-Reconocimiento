package com.sena.notification_service.application.usecase;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotificationTemplate;
import com.sena.notification_service.domain.model.OutboxEvent;
import com.sena.notification_service.domain.model.Recipient;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.domain.service.TemplateRenderer;
import com.sena.notification_service.port.in.ConsumeDomainEventCommand;
import com.sena.notification_service.port.in.ConsumeDomainEventUseCase;
import com.sena.notification_service.port.out.Notifier;
import com.sena.notification_service.port.out.NotificationRepository;
import com.sena.notification_service.port.out.RecipientResolver;
import com.sena.notification_service.port.out.TemplateRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("worker")
public class ConsumeDomainEventService implements ConsumeDomainEventUseCase {
    private static final String NOTIFICATION_SENT_EVENT = "notification.notification.sent";

    private final RecipientResolver recipientResolver;
    private final Notifier notifier;
    private final NotificationRepository repository;
    private final TemplateRepository templateRepository;

    public ConsumeDomainEventService(
            RecipientResolver recipientResolver,
            Notifier notifier,
            NotificationRepository repository,
            TemplateRepository templateRepository) {
        this.recipientResolver = recipientResolver;
        this.notifier = notifier;
        this.repository = repository;
        this.templateRepository = templateRepository;
    }

    @Override
    public void handle(ConsumeDomainEventCommand command) {
        RecipientReference reference = recipientReferenceFor(command);
        Recipient recipient = recipientResolver.resolve(reference.entityType(), reference.entityId());
        Instant now = Instant.now();

        SentNotification notification = new SentNotification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipient.id());
        notification.setRecipientEmail(recipient.email());
        notification.setChannel(Channel.EMAIL);
        notification.setSubject(reference.subject());
        notification.setSourceService(command.sourceService());
        notification.setSourceEventId(UUID.fromString(command.eventId()));
        notification.setCreatedAt(now);

        String templateCode = templateCodeFor(command.eventType());
        if (templateCode != null) {
            try {
                templateRepository.findByCode(templateCode)
                        .filter(NotificationTemplate::active)
                        .ifPresent(template -> {
                            Map<String, String> variables = templateVariablesFor(command.eventType(), command.payload());
                            notification.setSubject(TemplateRenderer.render(template.subjectTemplate(), variables));
                            notification.setBodySummary(TemplateRenderer.render(template.bodyTemplate(), variables));
                            notification.setTemplateId(template.id());
                        });
            } catch (RuntimeException ignored) {
                // Match the Go fallback behavior when template lookup is unavailable.
            }
        }

        try {
            notifier.send(notification);
            notification.setSendStatus(SendStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception ex) {
            notification.setSendStatus(SendStatus.FAILED);
            notification.setFailureReason(ex.getMessage());
        }

        OutboxEvent event = null;
        if (notification.getSendStatus() == SendStatus.SENT) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("notification_id", notification.getId().toString());
            payload.put("recipient_id", notification.getRecipientId().toString());
            payload.put("channel", notification.getChannel().name());
            payload.put("sent_at", notification.getSentAt());
            if (command.traceParent() != null && !command.traceParent().isBlank()) {
                payload.put("trace_parent", command.traceParent());
            }
            event = new OutboxEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    NOTIFICATION_SENT_EVENT,
                    payload,
                    now);
        }

        repository.saveWithOutbox(notification, event);
    }

    private RecipientReference recipientReferenceFor(ConsumeDomainEventCommand command) {
        Map<String, Object> payload = command.payload();
        return switch (command.eventType()) {
            case "monitoring.alert.triggered" -> {
                String entityType = stringValue(payload.get("affected_entity_type"));
                String entityId = stringValue(payload.get("affected_entity_id"));
                if (entityType.isBlank() || entityId.isBlank()) {
                    throw new IllegalArgumentException("monitoring.alert.triggered payload missing affected_entity_type/affected_entity_id");
                }
                String alertType = stringValue(payload.get("alert_type_code"));
                yield new RecipientReference(entityType, entityId, "Alert triggered: " + alertType);
            }
            case "scheduling.schedule.published" -> {
                String publishedBy = stringValue(payload.get("published_by"));
                if (publishedBy.isBlank()) {
                    throw new IllegalArgumentException("scheduling.schedule.published payload missing published_by");
                }
                yield new RecipientReference("Instructor", publishedBy, "Schedule published");
            }
            default -> throw new IllegalArgumentException("unsupported event_type: " + command.eventType());
        };
    }

    private String templateCodeFor(String eventType) {
        return switch (eventType) {
            case "monitoring.alert.triggered" -> "ALERT_TRIGGERED";
            case "scheduling.schedule.published" -> "SCHEDULE_PUBLISHED";
            default -> null;
        };
    }

    private Map<String, String> templateVariablesFor(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "monitoring.alert.triggered" -> Map.of(
                    "alert_type", stringValue(payload.get("alert_type_code")),
                    "ficha", stringValue(payload.get("affected_entity_id")));
            case "scheduling.schedule.published" -> Map.of(
                    "schedule_name", stringValue(payload.get("schedule_name")),
                    "ficha", stringValue(payload.get("ficha")));
            default -> Map.of();
        };
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }

    private record RecipientReference(String entityType, String entityId, String subject) {
    }
}
