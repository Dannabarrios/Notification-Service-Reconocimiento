package com.sena.notification_service.port.out;

import com.sena.notification_service.domain.model.SentNotification;

public interface Notifier {
    void send(SentNotification notification) throws Exception;
}
