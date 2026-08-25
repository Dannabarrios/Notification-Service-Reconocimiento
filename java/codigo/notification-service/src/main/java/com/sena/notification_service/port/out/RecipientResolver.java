package com.sena.notification_service.port.out;

import com.sena.notification_service.domain.model.Recipient;

public interface RecipientResolver {
    Recipient resolve(String entityType, String entityId);
}
