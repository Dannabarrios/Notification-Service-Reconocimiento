package com.sena.notification_service.adapter.in.amqp;

import com.rabbitmq.client.Channel;
import com.sena.notification_service.config.RabbitTopologyConfiguration;
import com.sena.notification_service.port.in.ConsumeDomainEventCommand;
import com.sena.notification_service.port.in.ConsumeDomainEventUseCase;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Profile("worker")
public class DomainEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(DomainEventConsumer.class);
    private static final Pattern EVENT_TYPE = Pattern.compile("^[a-z_]+\\.[a-z_]+\\.[a-z_]+$");

    private final JsonMapper jsonMapper;
    private final ConsumeDomainEventUseCase useCase;
    private final Tracer tracer;
    private final Propagator propagator;

    public DomainEventConsumer(
            JsonMapper jsonMapper,
            ConsumeDomainEventUseCase useCase,
            Tracer tracer,
            Propagator propagator) {
        this.jsonMapper = jsonMapper;
        this.useCase = useCase;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @RabbitListener(queues = RabbitTopologyConfiguration.QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        DomainEventEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(message.getBody(), DomainEventEnvelope.class);
            validate(envelope);
        } catch (Exception ex) {
            log.warn("notification-worker: rejecting invalid envelope: {}", ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String traceParent = activeTraceParent(message);
        try {
            useCase.handle(new ConsumeDomainEventCommand(
                    envelope.eventId(), envelope.eventType(), envelope.sourceService(), envelope.payload(), traceParent));
        } catch (RuntimeException ex) {
            // Preserve Go semantics: once an envelope is valid, business/dependency failures are logged and ACKed.
            log.error("notification-worker: failed to process event {} ({})", envelope.eventId(), envelope.eventType(), ex);
        }
        channel.basicAck(deliveryTag, false);
    }

    private String activeTraceParent(Message message) {
        Span current = tracer.currentSpan();
        if (current != null) {
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(current.context(), carrier, Map::put);
            String propagated = carrier.get("traceparent");
            if (propagated != null && !propagated.isBlank()) {
                return propagated;
            }
        }
        Object upstream = message.getMessageProperties().getHeaders().get("traceparent");
        return upstream == null ? null : upstream.toString();
    }

    private static void validate(DomainEventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventType() == null
                || envelope.payload() == null || envelope.sourceService() == null
                || envelope.timestamp() == null || envelope.version() == null) {
            throw new IllegalArgumentException("required domain event field is missing");
        }
        if (!EVENT_TYPE.matcher(envelope.eventType()).matches()) {
            throw new IllegalArgumentException("event_type must match ^[a-z_]+\\.[a-z_]+\\.[a-z_]+$");
        }
    }
}
