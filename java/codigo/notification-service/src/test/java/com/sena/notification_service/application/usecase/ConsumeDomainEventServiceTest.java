package com.sena.notification_service.application.usecase;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotificationTemplate;
import com.sena.notification_service.domain.model.OutboxEvent;
import com.sena.notification_service.domain.model.Recipient;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.in.ConsumeDomainEventCommand;
import com.sena.notification_service.port.out.NotificationRepository;
import com.sena.notification_service.port.out.Notifier;
import com.sena.notification_service.port.out.RecipientResolver;
import com.sena.notification_service.port.out.TemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumeDomainEventServiceTest {
    @Test
    void successfulDeliveryPersistsSentNotificationAndOutboxEvent() {
        UUID recipientId = UUID.randomUUID();
        CapturingRepository repo = new CapturingRepository();
        RecipientResolver resolver = (type, id) -> new Recipient(recipientId, "dev-notifications@sena.local");
        Notifier notifier = notification -> { };
        TemplateRepository templates = code -> Optional.of(new NotificationTemplate(
                UUID.randomUUID(), code, Channel.EMAIL,
                "Tu horario {{schedule_name}} fue publicado",
                "El horario {{schedule_name}} de la ficha {{ficha}} ha sido publicado.",
                true, Instant.now(), Instant.now()));
        ConsumeDomainEventService service = new ConsumeDomainEventService(resolver, notifier, repo, templates);
        UUID sourceEventId = UUID.randomUUID();

        service.handle(new ConsumeDomainEventCommand(
                sourceEventId.toString(), "scheduling.schedule.published", "scheduling-service",
                Map.of("published_by", recipientId.toString(), "schedule_name", "Demo", "ficha", "123"),
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"));

        assertEquals(SendStatus.SENT, repo.notification.getSendStatus());
        assertNotNull(repo.notification.getSentAt());
        assertEquals(sourceEventId, repo.notification.getSourceEventId());
        assertNotNull(repo.outbox);
        assertEquals("notification.notification.sent", repo.outbox.eventType());
        assertEquals("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01", repo.outbox.payload().get("trace_parent"));
    }

    @Test
    void deliveryFailurePersistsFailedNotificationWithoutOutbox() {
        UUID recipientId = UUID.randomUUID();
        CapturingRepository repo = new CapturingRepository();
        RecipientResolver resolver = (type, id) -> new Recipient(recipientId, "dev-notifications@sena.local");
        Notifier notifier = notification -> { throw new IllegalStateException("smtp down"); };
        TemplateRepository templates = code -> Optional.empty();
        ConsumeDomainEventService service = new ConsumeDomainEventService(resolver, notifier, repo, templates);

        service.handle(new ConsumeDomainEventCommand(
                UUID.randomUUID().toString(), "monitoring.alert.triggered", "monitoring-service",
                Map.of("affected_entity_type", "Instructor", "affected_entity_id", recipientId.toString(), "alert_type_code", "TEMP"),
                null));

        assertEquals(SendStatus.FAILED, repo.notification.getSendStatus());
        assertEquals("smtp down", repo.notification.getFailureReason());
        assertNull(repo.outbox);
    }

    static class CapturingRepository implements NotificationRepository {
        SentNotification notification;
        OutboxEvent outbox;
        @Override public SentNotification save(SentNotification notification) { this.notification = notification; return notification; }
        @Override public boolean saveWithOutbox(SentNotification notification, OutboxEvent event) {
            this.notification = notification;
            this.outbox = event;
            return false;
        }
        @Override public Optional<SentNotification> findById(UUID id) { return Optional.empty(); }
    }
}
