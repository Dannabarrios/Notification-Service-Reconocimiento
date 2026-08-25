package com.sena.notification_service.application.usecase;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotificationTemplate;
import com.sena.notification_service.domain.model.OutboxEvent;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.in.SendNotificationCommand;
import com.sena.notification_service.port.out.NotificationRepository;
import com.sena.notification_service.port.out.TemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendNotificationServiceTest {
    @Test
    void rendersActiveTemplateAndPersistsPendingNotification() {
        UUID templateId = UUID.randomUUID();
        TemplateRepository templates = code -> Optional.of(new NotificationTemplate(
                templateId, code, Channel.EMAIL,
                "Tu horario {{schedule_name}} fue publicado",
                "Ficha {{ficha}}", true, Instant.now(), Instant.now()));
        CapturingRepository repository = new CapturingRepository();
        SendNotificationService service = new SendNotificationService(repository, templates);

        SentNotification result = service.handle(new SendNotificationCommand(
                UUID.randomUUID(), "demo@sena.local", Channel.EMAIL, "fallback",
                "SCHEDULE_PUBLISHED", Map.of("schedule_name", "Demo", "ficha", "123"),
                "demo", null));

        assertEquals(SendStatus.PENDING, result.getSendStatus());
        assertEquals("Tu horario Demo fue publicado", result.getSubject());
        assertEquals("Ficha 123", result.getBodySummary());
        assertEquals(templateId, result.getTemplateId());
    }

    @Test
    void templateLookupFailureFallsBackToExplicitSubject() {
        TemplateRepository templates = code -> { throw new IllegalStateException("db down"); };
        CapturingRepository repository = new CapturingRepository();
        SendNotificationService service = new SendNotificationService(repository, templates);

        SentNotification result = service.handle(new SendNotificationCommand(
                UUID.randomUUID(), "demo@sena.local", Channel.EMAIL, "explicit",
                "SCHEDULE_PUBLISHED", Map.of(), null, null));

        assertEquals("explicit", result.getSubject());
        assertNull(result.getTemplateId());
    }

    static class CapturingRepository implements NotificationRepository {
        @Override public SentNotification save(SentNotification notification) {
            notification.setId(UUID.randomUUID());
            return notification;
        }
        @Override public boolean saveWithOutbox(SentNotification notification, OutboxEvent event) { return false; }
        @Override public Optional<SentNotification> findById(UUID id) { return Optional.empty(); }
    }
}
