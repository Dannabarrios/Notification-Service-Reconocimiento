package com.sena.notification_service.adapter.out.notifier;

import com.sena.notification_service.domain.model.SentNotification;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class InAppNotifier {
    public void send(SentNotification notification) {
        // The Go implementation intentionally treats IN_APP as a no-op adapter for now.
    }
}
