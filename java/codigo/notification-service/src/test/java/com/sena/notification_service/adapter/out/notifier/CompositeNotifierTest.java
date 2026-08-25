package com.sena.notification_service.adapter.out.notifier;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.SentNotification;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CompositeNotifierTest {
    @Test
    void dispatchesEmailAndRecordsSentMetric() throws Exception {
        SmtpNotifier smtp = mock(SmtpNotifier.class);
        InAppNotifier inApp = mock(InAppNotifier.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CompositeNotifier notifier = new CompositeNotifier(smtp, inApp, metrics);
        SentNotification notification = new SentNotification();
        notification.setChannel(Channel.EMAIL);

        notifier.send(notification);

        verify(smtp).send(notification);
        verify(inApp, never()).send(notification);
        assertEquals(1.0, metrics.counter(
                "notification.delivered", "channel", "EMAIL", "status", "SENT").count());
    }

    @Test
    void dispatchesInAppAndRecordsSentMetric() throws Exception {
        SmtpNotifier smtp = mock(SmtpNotifier.class);
        InAppNotifier inApp = mock(InAppNotifier.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CompositeNotifier notifier = new CompositeNotifier(smtp, inApp, metrics);
        SentNotification notification = new SentNotification();
        notification.setChannel(Channel.IN_APP);

        notifier.send(notification);

        verify(inApp).send(notification);
        verify(smtp, never()).send(notification);
        assertEquals(1.0, metrics.counter(
                "notification.delivered", "channel", "IN_APP", "status", "SENT").count());
    }
}
