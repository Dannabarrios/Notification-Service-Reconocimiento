package com.sena.notification_service.domain.model;

import java.util.UUID;

public record Recipient(UUID id, String email) {
}
