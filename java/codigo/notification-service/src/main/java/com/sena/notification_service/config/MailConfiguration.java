package com.sena.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@Profile("worker")
public class MailConfiguration {
    @Bean
    JavaMailSender javaMailSender() {
        String address = requireEnv("NOTIFICATION_SMTP_ADDR");
        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            throw new IllegalArgumentException("NOTIFICATION_SMTP_ADDR must have host:port format");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(address.substring(0, separator));
        sender.setPort(Integer.parseInt(address.substring(separator + 1)));
        return sender;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required; no default is provided for connection secrets");
        }
        return value;
    }
}
