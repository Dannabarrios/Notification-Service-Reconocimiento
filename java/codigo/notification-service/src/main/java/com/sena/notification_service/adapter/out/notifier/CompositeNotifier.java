package com.sena.notification_service.adapter.out.notifier;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.out.Notifier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class CompositeNotifier implements Notifier {
    private final SmtpNotifier smtp;
    private final InAppNotifier inApp;
    private final MeterRegistry metrics;

    public CompositeNotifier(SmtpNotifier smtp, InAppNotifier inApp, MeterRegistry metrics) {
        this.smtp = smtp;
        this.inApp = inApp;
        this.metrics = metrics;
    }

    @Override
    public void send(SentNotification notification) throws Exception {
        try {
            if (notification.getChannel() == Channel.EMAIL) {
                smtp.send(notification);
            } else if (notification.getChannel() == Channel.IN_APP) {
                inApp.send(notification);
            } else {
                throw new IllegalArgumentException("unsupported channel: " + notification.getChannel());
            }
            metrics.counter("notification.delivered", "channel", notification.getChannel().name(), "status", "SENT").increment();
        } catch (RuntimeException ex) {
            metrics.counter("notification.delivered", "channel", notification.getChannel().name(), "status", "FAILED").increment();
            throw ex;
        }
    }
}
