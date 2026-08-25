package com.sena.notification_service.adapter.in.http;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotFoundException;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.in.GetNotificationQuery;
import com.sena.notification_service.port.in.GetNotificationUseCase;
import com.sena.notification_service.port.in.SendNotificationCommand;
import com.sena.notification_service.port.in.SendNotificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@Profile("api")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private final SendNotificationUseCase sendNotification;
    private final GetNotificationUseCase getNotification;

    public NotificationController(SendNotificationUseCase sendNotification, GetNotificationUseCase getNotification) {
        this.sendNotification = sendNotification;
        this.getNotification = getNotification;
    }

    @PostMapping
    public ResponseEntity<?> send(@RequestBody SendNotificationRequest request) {
        if (request.recipientId() == null || NIL_UUID.equals(request.recipientId())
                || isMissing(request.recipientEmail()) || isMissing(request.subject())) {
            return ResponseEntity.badRequest().body(ErrorEnvelope.of(
                    "VALIDATION_ERROR", "recipient_id, recipient_email y subject son requeridos"));
        }

        Channel channel;
        try {
            channel = Channel.valueOf(request.channel() == null ? "" : request.channel());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ErrorEnvelope.of(
                    "VALIDATION_ERROR", "channel debe ser EMAIL o IN_APP"));
        }

        SendNotificationCommand command = new SendNotificationCommand(
                request.recipientId(),
                request.recipientEmail(),
                channel,
                request.subject(),
                request.templateCode(),
                request.templateVars() == null ? Map.of() : request.templateVars(),
                request.sourceService(),
                request.sourceEventId());
        try {
            SentNotification result = sendNotification.handle(command);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(SentNotificationResponse.from(result));
        } catch (RuntimeException ex) {
            log.error("failed to persist notification", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorEnvelope.of(
                    "DEPENDENCY_UNAVAILABLE", "no se pudo persistir la notificacion"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        UUID notificationId;
        try {
            notificationId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ErrorEnvelope.of(
                    "VALIDATION_ERROR", "id must be a valid UUID"));
        }

        try {
            return ResponseEntity.ok(SentNotificationResponse.from(
                    getNotification.handle(new GetNotificationQuery(notificationId))));
        } catch (NotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorEnvelope.of(
                    "NOT_FOUND", "notification not found"));
        } catch (RuntimeException ex) {
            log.error("failed to retrieve notification", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorEnvelope.of(
                    "DEPENDENCY_UNAVAILABLE", "error retrieving notification"));
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.isEmpty();
    }
}
