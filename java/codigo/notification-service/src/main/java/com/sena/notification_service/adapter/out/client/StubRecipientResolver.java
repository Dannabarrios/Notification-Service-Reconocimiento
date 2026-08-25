package com.sena.notification_service.adapter.out.client;

import com.sena.notification_service.domain.model.Recipient;
import com.sena.notification_service.port.out.RecipientResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("worker")
public class StubRecipientResolver implements RecipientResolver {
    private final String email;

    public StubRecipientResolver() {
        String configured = System.getenv("NOTIFICATION_RECIPIENT_STUB_EMAIL");
        this.email = configured == null || configured.isBlank() ? "dev-notifications@sena.local" : configured;
    }

    @Override
    public Recipient resolve(String entityType, String entityId) {
        return new Recipient(UUID.fromString(entityId), email);
    }
}
