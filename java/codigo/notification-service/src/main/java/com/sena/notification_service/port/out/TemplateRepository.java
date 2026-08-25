package com.sena.notification_service.port.out;

import com.sena.notification_service.domain.model.NotificationTemplate;

import java.util.Optional;

public interface TemplateRepository {
    Optional<NotificationTemplate> findByCode(String code);
}
