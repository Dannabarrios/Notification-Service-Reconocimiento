package com.sena.notification_service.adapter.in.http;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotFoundException;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.in.GetNotificationUseCase;
import com.sena.notification_service.port.in.SendNotificationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotificationControllerTest {
    @Test
    void postReturns202WithGoCompatibleResponseShape() {
        UUID id = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        SendNotificationUseCase send = command -> {
            SentNotification n = new SentNotification();
            n.setId(id);
            n.setRecipientId(command.recipientId());
            n.setChannel(command.channel());
            n.setSubject(command.subject());
            n.setSendStatus(SendStatus.PENDING);
            return n;
        };
        GetNotificationUseCase get = query -> { throw new NotFoundException("notification not found"); };
        NotificationController controller = new NotificationController(send, get);

        ResponseEntity<?> response = controller.send(new SendNotificationRequest(
                recipient, "demo@sena.local", "EMAIL", "Demo", null, Map.of(), null, null));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        SentNotificationResponse body = assertInstanceOf(SentNotificationResponse.class, response.getBody());
        assertEquals(id, body.id());
        assertEquals(recipient, body.recipientId());
        assertEquals(Channel.EMAIL, body.channel());
        assertEquals(SendStatus.PENDING, body.sendStatus());
    }

    @Test
    void zeroRecipientUuidIsRejectedLikeTheGoService() {
        NotificationController controller = new NotificationController(command -> null, query -> null);

        ResponseEntity<?> response = controller.send(new SendNotificationRequest(
                new UUID(0L, 0L), "demo@sena.local", "EMAIL", "Demo", null, Map.of(), null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorEnvelope body = assertInstanceOf(ErrorEnvelope.class, response.getBody());
        assertEquals("VALIDATION_ERROR", body.errorCode());
        assertEquals("recipient_id, recipient_email y subject son requeridos", body.message());
    }

    @Test
    void invalidChannelReturnsValidationError() {
        NotificationController controller = new NotificationController(command -> null, query -> null);
        ResponseEntity<?> response = controller.send(new SendNotificationRequest(
                UUID.randomUUID(), "demo@sena.local", "SMS", "Demo", null, Map.of(), null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorEnvelope body = assertInstanceOf(ErrorEnvelope.class, response.getBody());
        assertEquals("VALIDATION_ERROR", body.errorCode());
        assertEquals("channel debe ser EMAIL o IN_APP", body.message());
    }

    @Test
    void getReturns200ForExistingNotification() {
        UUID id = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        SentNotification notification = new SentNotification();
        notification.setId(id);
        notification.setRecipientId(recipient);
        notification.setChannel(Channel.IN_APP);
        notification.setSubject("Notificacion interna");
        notification.setSendStatus(SendStatus.PENDING);
        NotificationController controller = new NotificationController(command -> null, query -> notification);

        ResponseEntity<?> response = controller.get(id.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SentNotificationResponse body = assertInstanceOf(SentNotificationResponse.class, response.getBody());
        assertEquals(id, body.id());
        assertEquals(Channel.IN_APP, body.channel());
    }

    @Test
    void getReturns404ForMissingNotification() {
        UUID id = UUID.randomUUID();
        NotificationController controller = new NotificationController(
                command -> null,
                query -> { throw new NotFoundException("notification not found"); });

        ResponseEntity<?> response = controller.get(id.toString());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorEnvelope body = assertInstanceOf(ErrorEnvelope.class, response.getBody());
        assertEquals("NOT_FOUND", body.errorCode());
    }

    @Test
    void getRejectsMalformedUuid() {
        NotificationController controller = new NotificationController(command -> null, query -> null);

        ResponseEntity<?> response = controller.get("no-es-un-uuid");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorEnvelope body = assertInstanceOf(ErrorEnvelope.class, response.getBody());
        assertEquals("VALIDATION_ERROR", body.errorCode());
        assertEquals("id must be a valid UUID", body.message());
    }
}
