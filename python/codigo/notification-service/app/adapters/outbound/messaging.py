from __future__ import annotations

import json
import logging
import threading
from datetime import datetime
from typing import Any

from opentelemetry import propagate, trace
from opentelemetry.trace import SpanKind, Status, StatusCode

NOTIFICATION_EXCHANGE = "notification-events"
logger = logging.getLogger("notification-worker")


def _iso(value: datetime) -> str:
    return value.isoformat().replace("+00:00", "Z")


class RabbitMQPublisher:
    def __init__(self, amqp_url: str):
        self.amqp_url = amqp_url
        self.connection = None
        self.channel = None

    def connect(self) -> None:
        import pika

        self.connection = pika.BlockingConnection(pika.URLParameters(self.amqp_url))
        self.channel = self.connection.channel()
        self.channel.exchange_declare(
            exchange=NOTIFICATION_EXCHANGE,
            exchange_type="topic",
            durable=True,
        )

    def publish(self, event_type: str, event_id: str, body: bytes, headers: dict[str, str]) -> None:
        import pika

        if self.connection is None or self.channel is None:
            self.connect()
        self.channel.basic_publish(
            exchange=NOTIFICATION_EXCHANGE,
            routing_key=event_type,
            body=body,
            properties=pika.BasicProperties(
                content_type="application/json",
                message_id=event_id,
                headers=headers,
            ),
        )

    def close(self) -> None:
        if self.channel is not None and getattr(self.channel, "is_open", False):
            self.channel.close()
        if self.connection is not None and getattr(self.connection, "is_open", False):
            self.connection.close()


class OutboxRelay:
    def __init__(self, database, publisher: RabbitMQPublisher, tracer=None):
        self.database = database
        self.publisher = publisher
        self.tracer = tracer or trace.get_tracer("notification-service/adapters/outbound/messaging")

    def poll_and_publish(self, limit: int = 20) -> int:
        select_query = """
            SELECT id::text, event_id::text, event_type, payload, created_at
            FROM notification.outbox
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT %s
            FOR UPDATE SKIP LOCKED
        """
        update_query = "UPDATE notification.outbox SET published_at = now() WHERE id = %s::uuid"
        published = 0
        with self.database.pool.connection() as connection:
            with connection.transaction():
                with connection.cursor() as cursor:
                    cursor.execute(select_query, (limit,))
                    staged = cursor.fetchall()
                    for outbox_id, event_id, event_type, payload, created_at in staged:
                        self._publish_one(event_id, event_type, payload, created_at)
                        cursor.execute(update_query, (outbox_id,))
                        published += 1
        return published

    def _publish_one(
        self,
        event_id: str,
        event_type: str,
        payload: dict[str, Any],
        created_at: datetime,
    ) -> None:
        trace_parent = payload.get("trace_parent") if isinstance(payload, dict) else None
        parent_context = propagate.extract(
            carrier={"traceparent": trace_parent} if isinstance(trace_parent, str) and trace_parent else {}
        )
        with self.tracer.start_as_current_span(
            f"outbox.publish {event_type}",
            context=parent_context,
            kind=SpanKind.PRODUCER,
            attributes={
                "messaging.system": "rabbitmq",
                "messaging.destination": NOTIFICATION_EXCHANGE,
                "event.type": event_type,
                "event.id": event_id,
            },
        ) as span:
            envelope = {
                "event_id": event_id,
                "event_type": event_type,
                "version": "1.0",
                "timestamp": _iso(created_at),
                "source_service": "notification-service",
                "payload": payload,
            }
            body = json.dumps(envelope, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            headers: dict[str, str] = {}
            propagate.inject(headers)
            try:
                self.publisher.publish(event_type, event_id, body, headers)
            except Exception as exc:
                span.record_exception(exc)
                span.set_status(Status(StatusCode.ERROR, str(exc)))
                raise

    def run(self, stop_event: threading.Event, interval_seconds: float = 2.0, limit: int = 20) -> None:
        while not stop_event.wait(interval_seconds):
            try:
                self.poll_and_publish(limit)
            except Exception:
                logger.exception("outbox relay error")
