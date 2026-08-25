package com.sena.notification_service.adapter.out.notifier;

import com.sena.notification_service.domain.model.SentNotification;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class SmtpNotifier {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        String configured = System.getenv("NOTIFICATION_SMTP_FROM");
        this.from = configured == null || configured.isBlank() ? "notifications@sena.local" : configured;
    }

    public void send(SentNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipientEmail());
        message.setSubject(notification.getSubject());
        message.setText(notification.getSubject());
        mailSender.send(message);
    }
}
