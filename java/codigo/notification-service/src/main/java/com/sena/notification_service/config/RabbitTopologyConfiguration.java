package com.sena.notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("worker")
public class RabbitTopologyConfiguration {
    public static final String SCHEDULING_EXCHANGE = "scheduling-events";
    public static final String MONITORING_EXCHANGE = "monitoring-events";
    public static final String NOTIFICATION_EXCHANGE = "notification-events";
    public static final String QUEUE = "notification-service.events";

    @Bean
    Declarables notificationTopology() {
        TopicExchange scheduling = new TopicExchange(SCHEDULING_EXCHANGE, true, false);
        TopicExchange monitoring = new TopicExchange(MONITORING_EXCHANGE, true, false);
        TopicExchange notifications = new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
        Queue queue = new Queue(QUEUE, true, false, false);
        Binding scheduleBinding = BindingBuilder.bind(queue).to(scheduling).with("scheduling.schedule.published");
        Binding alertBinding = BindingBuilder.bind(queue).to(monitoring).with("monitoring.alert.triggered");
        return new Declarables(scheduling, monitoring, notifications, queue, scheduleBinding, alertBinding);
    }
}
