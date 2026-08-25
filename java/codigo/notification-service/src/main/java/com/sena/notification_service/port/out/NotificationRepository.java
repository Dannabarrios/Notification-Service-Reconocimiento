package com.sena.notification_service.port.out;

import com.sena.notification_service.domain.model.OutboxEvent;
import com.sena.notification_service.domain.model.SentNotification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    SentNotification save(SentNotification notification);
    boolean saveWithOutbox(SentNotification notification, OutboxEvent event);
    Optional<SentNotification> findById(UUID id);
}
