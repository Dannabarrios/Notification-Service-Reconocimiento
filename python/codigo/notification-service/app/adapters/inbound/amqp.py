from __future__ import annotations

import json
import logging
from typing import Any

from opentelemetry import propagate, trace
from opentelemetry.trace import SpanKind, Status, StatusCode

from app.application.commands import ConsumeDomainEventCommand
from app.contracts import DomainEventEnvelope

EXCHANGE_SCHEDULING = "scheduling-events"
EXCHANGE_MONITORING = "monitoring-events"
ROUTING_KEY_SCHEDULE_PUBLISHED = "scheduling.schedule.published"
ROUTING_KEY_ALERT_TRIGGERED = "monitoring.alert.triggered"
QUEUE_NAME = "notification-service.events"

logger = logging.getLogger("notification-worker")


def _normalized_headers(headers: dict[str, Any] | None) -> dict[str, str]:
    result: dict[str, str] = {}
    for key, value in (headers or {}).items():
        if isinstance(value, bytes):
            value = value.decode("utf-8", errors="replace")
        result[str(key)] = str(value)
    return result


class RabbitMQConsumer:
    def __init__(self, amqp_url: str, use_case, tracer=None):
        self.amqp_url = amqp_url
        self.use_case = use_case
        self.tracer = tracer or trace.get_tracer("notification-service/adapters/inbound/amqp")
        self.connection = None
        self.channel = None

    def connect(self) -> None:
        import pika

        self.connection = pika.BlockingConnection(pika.URLParameters(self.amqp_url))
        self.channel = self.connection.channel()
        for exchange in (EXCHANGE_SCHEDULING, EXCHANGE_MONITORING):
            self.channel.exchange_declare(exchange=exchange, exchange_type="topic", durable=True)
        self.channel.queue_declare(queue=QUEUE_NAME, durable=True)
        self.channel.queue_bind(
            queue=QUEUE_NAME,
            exchange=EXCHANGE_SCHEDULING,
            routing_key=ROUTING_KEY_SCHEDULE_PUBLISHED,
        )
        self.channel.queue_bind(
            queue=QUEUE_NAME,
            exchange=EXCHANGE_MONITORING,
            routing_key=ROUTING_KEY_ALERT_TRIGGERED,
        )

    def run(self) -> None:
        if self.connection is None or self.channel is None:
            self.connect()
        self.channel.basic_consume(
            queue=QUEUE_NAME,
            on_message_callback=self.handle_delivery,
            auto_ack=False,
            consumer_tag="notification-service",
        )
        self.channel.start_consuming()

    def handle_delivery(self, channel, method, properties, body: bytes) -> None:
        try:
            raw = json.loads(body)
            envelope = DomainEventEnvelope.model_validate(raw)
        except Exception as exc:
            logger.warning("notification-worker: rejecting invalid envelope: %s", exc)
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            return

        carrier = _normalized_headers(getattr(properties, "headers", None))
        parent_context = propagate.extract(carrier=carrier)
        with self.tracer.start_as_current_span(
            f"amqp.consume {envelope.event_type}",
            context=parent_context,
            kind=SpanKind.CONSUMER,
            attributes={
                "messaging.system": "rabbitmq",
                "messaging.destination": QUEUE_NAME,
                "event.type": envelope.event_type,
                "event.id": envelope.event_id,
            },
        ) as span:
            trace_carrier: dict[str, str] = {}
            propagate.inject(trace_carrier)
            command = ConsumeDomainEventCommand(
                event_id=envelope.event_id,
                event_type=envelope.event_type,
                source_service=envelope.source_service,
                payload=envelope.payload or {},
                trace_parent=trace_carrier.get("traceparent", ""),
            )
            try:
                self.use_case.handle(command)
            except Exception as exc:
                span.record_exception(exc)
                span.set_status(Status(StatusCode.ERROR, str(exc)))
                logger.error(
                    "notification-worker: failed to process event %s (%s): %s",
                    envelope.event_id,
                    envelope.event_type,
                    exc,
                )
        channel.basic_ack(delivery_tag=method.delivery_tag)

    def ping(self) -> None:
        if self.connection is None or not getattr(self.connection, "is_open", False):
            raise RuntimeError("amqp connection is closed")

    def close(self) -> None:
        if self.channel is not None and getattr(self.channel, "is_open", False):
            self.channel.close()
        if self.connection is not None and getattr(self.connection, "is_open", False):
            self.connection.close()
