package com.sena.notification_service.application.usecase;

import com.sena.notification_service.domain.model.NotFoundException;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.in.GetNotificationQuery;
import com.sena.notification_service.port.in.GetNotificationUseCase;
import com.sena.notification_service.port.out.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class GetNotificationService implements GetNotificationUseCase {
    private final NotificationRepository repository;

    public GetNotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public SentNotification handle(GetNotificationQuery query) {
        return repository.findById(query.id())
                .orElseThrow(() -> new NotFoundException("notification not found"));
    }
}
