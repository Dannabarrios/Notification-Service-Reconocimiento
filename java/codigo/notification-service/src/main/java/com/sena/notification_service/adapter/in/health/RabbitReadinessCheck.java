package com.sena.notification_service.adapter.in.health;

import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class RabbitReadinessCheck implements ReadinessCheck {
    private final ConnectionFactory connectionFactory;

    public RabbitReadinessCheck(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public String name() {
        return "broker";
    }

    @Override
    public void check() {
        Connection connection = connectionFactory.createConnection();
        try {
            if (!connection.isOpen()) {
                throw new IllegalStateException("amqp connection is closed");
            }
        } finally {
            connection.close();
        }
    }
}
