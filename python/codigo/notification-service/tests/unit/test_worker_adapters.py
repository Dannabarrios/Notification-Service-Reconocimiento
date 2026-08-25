from __future__ import annotations

import json
from datetime import UTC, datetime
from types import SimpleNamespace

from app.adapters.inbound.amqp import RabbitMQConsumer
from app.adapters.outbound.notifier import CompositeNotifier, SMTPNotifier
from app.application.commands import ConsumeDomainEventCommand
from app.domain.models import Channel, SentNotification


class FakeUseCase:
    def __init__(self, error=None):
        self.command = None
        self.error = error

    def handle(self, command):
        self.command = command
        if self.error:
            raise self.error


class FakeChannel:
    def __init__(self):
        self.acked = []
        self.nacked = []

    def basic_ack(self, delivery_tag):
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag, requeue):
        self.nacked.append((delivery_tag, requeue))


def valid_envelope(event_id="33333333-3333-3333-3333-333333333333"):
    return json.dumps(
        {
            "event_id": event_id,
            "event_type": "monitoring.alert.triggered",
            "version": "1.0",
            "timestamp": datetime.now(UTC).isoformat(),
            "source_service": "monitoring-service",
            "payload": {
                "affected_entity_type": "Learner",
                "affected_entity_id": "10101010-1010-1010-1010-101010101010",
            },
        }
    ).encode()


def test_consumer_valid_envelope_calls_use_case_and_acks_even_on_use_case_error():
    use_case = FakeUseCase(error=RuntimeError("temporary resolver failure"))
    consumer = RabbitMQConsumer("amqp://unused", use_case)
    channel = FakeChannel()
    method = SimpleNamespace(delivery_tag=7)
    properties = SimpleNamespace(headers={})

    consumer.handle_delivery(channel, method, properties, valid_envelope())

    assert isinstance(use_case.command, ConsumeDomainEventCommand)
    assert use_case.command.event_type == "monitoring.alert.triggered"
    assert channel.acked == [7]
    assert channel.nacked == []


def test_consumer_invalid_envelope_nacks_without_requeue():
    use_case = FakeUseCase()
    consumer = RabbitMQConsumer("amqp://unused", use_case)
    channel = FakeChannel()
    consumer.handle_delivery(
        channel,
        SimpleNamespace(delivery_tag=8),
        SimpleNamespace(headers={}),
        b"not-json",
    )
    assert use_case.command is None
    assert channel.acked == []
    assert channel.nacked == [(8, False)]


def test_composite_notifier_dispatches_by_channel():
    calls = []

    class Email:
        def send(self, notification):
            calls.append(("email", notification.subject))

    class InApp:
        def send(self, notification):
            calls.append(("inapp", notification.subject))

    composite = CompositeNotifier(Email(), InApp())
    composite.send(SentNotification(channel=Channel.EMAIL, subject="hello"))
    composite.send(SentNotification(channel=Channel.IN_APP, subject="inside"))

    assert calls == [("email", "hello"), ("inapp", "inside")]


def test_consumer_propagates_current_consume_traceparent():
    from opentelemetry.sdk.trace import TracerProvider

    use_case = FakeUseCase()
    tracer = TracerProvider().get_tracer("test-consumer")
    consumer = RabbitMQConsumer("amqp://unused", use_case, tracer=tracer)
    channel = FakeChannel()
    upstream = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"

    consumer.handle_delivery(
        channel,
        SimpleNamespace(delivery_tag=9),
        SimpleNamespace(headers={"traceparent": upstream}),
        valid_envelope(),
    )

    assert use_case.command.trace_parent.startswith("00-0123456789abcdef0123456789abcdef-")
    assert channel.acked == [9]


def test_smtp_notifier_matches_go_wire_message(monkeypatch):
    sent = {}

    class FakeSMTP:
        def __init__(self, host, port):
            sent["host"] = host
            sent["port"] = port

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def sendmail(self, from_address, recipients, message):
            sent["from"] = from_address
            sent["recipients"] = recipients
            sent["message"] = message.decode("utf-8")

    monkeypatch.setattr("smtplib.SMTP", FakeSMTP)
    notifier = SMTPNotifier("mailhog:1025", "notifications@sena.local")
    notifier.send(
        SentNotification(
            recipient_email="demo@sena.local",
            channel=Channel.EMAIL,
            subject="Rendered subject",
            body_summary="Rendered body that Go intentionally does not send over SMTP",
        )
    )

    assert sent["host"] == "mailhog"
    assert sent["port"] == 1025
    assert sent["from"] == "notifications@sena.local"
    assert sent["recipients"] == ["demo@sena.local"]
    assert sent["message"].endswith("\r\n\r\nRendered subject\r\n")
    assert "Rendered body that Go intentionally does not send" not in sent["message"]
