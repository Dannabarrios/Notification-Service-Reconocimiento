package com.sena.notification_service.adapter.in.amqp;

import com.rabbitmq.client.Channel;
import com.sena.notification_service.port.in.ConsumeDomainEventUseCase;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainEventConsumerTest {
    @Test
    void invalidEnvelopeIsNackedWithoutRequeue() throws Exception {
        Channel channel = mock(Channel.class);
        ConsumeDomainEventUseCase useCase = mock(ConsumeDomainEventUseCase.class);
        DomainEventConsumer consumer = consumer(useCase);

        consumer.consume(message("no-es-json", 10L), channel);

        verify(channel).basicNack(10L, false, false);
        verify(channel, never()).basicAck(10L, false);
        verify(useCase, never()).handle(any());
    }

    @Test
    void validEnvelopeIsAckedEvenWhenUseCaseFails() throws Exception {
        Channel channel = mock(Channel.class);
        ConsumeDomainEventUseCase useCase = mock(ConsumeDomainEventUseCase.class);
        doThrow(new IllegalStateException("unsupported event")).when(useCase).handle(any());
        DomainEventConsumer consumer = consumer(useCase);
        String json = """
                {
                  "event_id":"55555555-5555-4555-8555-555555555555",
                  "event_type":"monitoring.alert.triggered",
                  "version":"1.0",
                  "timestamp":"2026-08-25T00:00:00Z",
                  "source_service":"monitoring-service",
                  "payload":{"affected_entity_type":"Learner"}
                }
                """;

        consumer.consume(message(json, 11L), channel);

        verify(useCase).handle(any());
        verify(channel).basicAck(11L, false);
        verify(channel, never()).basicNack(11L, false, false);
    }

    private static DomainEventConsumer consumer(ConsumeDomainEventUseCase useCase) {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);
        return new DomainEventConsumer(JsonMapper.builder().build(), useCase, tracer, mock(Propagator.class));
    }

    private static Message message(String body, long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
