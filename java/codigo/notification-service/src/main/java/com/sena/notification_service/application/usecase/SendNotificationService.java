package com.sena.notification_service.application.usecase;

import com.sena.notification_service.domain.model.NotificationTemplate;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.domain.service.TemplateRenderer;
import com.sena.notification_service.port.in.SendNotificationCommand;
import com.sena.notification_service.port.in.SendNotificationUseCase;
import com.sena.notification_service.port.out.NotificationRepository;
import com.sena.notification_service.port.out.TemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SendNotificationService implements SendNotificationUseCase {
    private final NotificationRepository repository;
    private final TemplateRepository templateRepository;

    public SendNotificationService(NotificationRepository repository, TemplateRepository templateRepository) {
        this.repository = repository;
        this.templateRepository = templateRepository;
    }

    @Override
    public SentNotification handle(SendNotificationCommand command) {
        SentNotification notification = new SentNotification();
        notification.setRecipientId(command.recipientId());
        notification.setRecipientEmail(command.recipientEmail());
        notification.setChannel(command.channel());
        notification.setSubject(command.subject());
        notification.setSendStatus(SendStatus.PENDING);
        notification.setSourceService(command.sourceService());
        notification.setSourceEventId(command.sourceEventId());
        notification.setCreatedAt(Instant.now());

        if (command.templateCode() != null && !command.templateCode().isBlank()) {
            try {
                templateRepository.findByCode(command.templateCode())
                        .filter(NotificationTemplate::active)
                        .ifPresent(template -> {
                            notification.setSubject(TemplateRenderer.render(template.subjectTemplate(), command.templateVars()));
                            notification.setBodySummary(TemplateRenderer.render(template.bodyTemplate(), command.templateVars()));
                            notification.setTemplateId(template.id());
                        });
            } catch (RuntimeException ignored) {
                // Go behavior intentionally falls back to the explicit subject on template lookup errors.
            }
        }
        return repository.save(notification);
    }
}
