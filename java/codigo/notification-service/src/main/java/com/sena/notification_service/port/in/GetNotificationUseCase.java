package com.sena.notification_service.port.in;

import com.sena.notification_service.domain.model.SentNotification;

public interface GetNotificationUseCase {
    SentNotification handle(GetNotificationQuery query);
}
