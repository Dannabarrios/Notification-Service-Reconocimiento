from __future__ import annotations

from datetime import UTC, datetime

import pytest

from app.application.commands import ConsumeDomainEventCommand, GetNotificationQuery, SendNotificationCommand
from app.application.use_cases import ConsumeDomainEvent, GetNotification, SendNotification
from app.domain.models import (
    Channel,
    NotificationNotFoundError,
    NotificationTemplate,
    Recipient,
    SendStatus,
)


class FakeRepo:
    def __init__(self):
        self.saved = []
        self.saved_with_outbox = []
        self.found = None
        self.already_processed = False

    def save(self, notification):
        notification.id = notification.id or "11111111-1111-1111-1111-111111111111"
        notification.created_at = notification.created_at or datetime.now(UTC)
        self.saved.append(notification)

    def save_with_outbox(self, notification, event):
        self.saved_with_outbox.append((notification, event))
        return self.already_processed

    def find_by_id(self, notification_id):
        return self.found


class FakeTemplates:
    def __init__(self, template=None, error=None):
        self.template = template
        self.error = error

    def find_by_code(self, code):
        if self.error:
            raise self.error
        return self.template


class FakeResolver:
    def __init__(self, recipient=None):
        self.recipient = recipient or Recipient(id="10101010-1010-1010-1010-101010101010", email="dev@sena.local")

    def resolve(self, entity_type, entity_id):
        return self.recipient


class FakeNotifier:
    def __init__(self, error=None):
        self.error = error
        self.sent = []

    def send(self, notification):
        self.sent.append(notification)
        if self.error:
            raise self.error


def test_send_notification_sets_pending_and_renders_active_template():
    repo = FakeRepo()
    templates = FakeTemplates(
        NotificationTemplate(
            id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            code="SCHEDULE_PUBLISHED",
            channel=Channel.EMAIL,
            subject_template="Tu horario {{schedule_name}} fue publicado",
            body_template="El horario {{schedule_name}} de la ficha {{ficha}} ha sido publicado.",
            is_active=True,
        )
    )
    use_case = SendNotification(repo, templates)

    result = use_case.handle(
        SendNotificationCommand(
            recipient_id="22222222-2222-2222-2222-222222222222",
            recipient_email="a@b.co",
            channel=Channel.EMAIL,
            subject="fallback",
            template_code="SCHEDULE_PUBLISHED",
            template_vars={"schedule_name": "Ene-2026", "ficha": "2850621"},
            source_service="monitoring",
            source_event_id="33333333-3333-3333-3333-333333333333",
        )
    )

    assert result.send_status == SendStatus.PENDING
    assert result.subject == "Tu horario Ene-2026 fue publicado"
    assert result.body_summary == "El horario Ene-2026 de la ficha 2850621 ha sido publicado."
    assert result.template_id == "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    assert len(repo.saved) == 1


def test_send_notification_ignores_template_repository_failure_and_uses_explicit_subject():
    repo = FakeRepo()
    use_case = SendNotification(repo, FakeTemplates(error=RuntimeError("template db down")))

    result = use_case.handle(
        SendNotificationCommand(
            recipient_id="22222222-2222-2222-2222-222222222222",
            recipient_email="a@b.co",
            channel=Channel.EMAIL,
            subject="explicit subject",
            template_code="SCHEDULE_PUBLISHED",
        )
    )

    assert result.subject == "explicit subject"
    assert result.template_id == ""


def test_get_notification_missing_raises_domain_not_found():
    repo = FakeRepo()
    use_case = GetNotification(repo)
    with pytest.raises(NotificationNotFoundError):
        use_case.handle(GetNotificationQuery(id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))


def test_consume_alert_success_marks_sent_and_stages_outbox_with_trace_parent():
    repo = FakeRepo()
    templates = FakeTemplates(
        NotificationTemplate(
            id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            code="ALERT_TRIGGERED",
            channel=Channel.IN_APP,
            subject_template="Alerta: {{alert_type}}",
            body_template="Se genero una alerta {{alert_type}} en la ficha {{ficha}}.",
            is_active=True,
        )
    )
    use_case = ConsumeDomainEvent(FakeResolver(), FakeNotifier(), repo, templates)

    use_case.handle(
        ConsumeDomainEventCommand(
            event_id="33333333-3333-3333-3333-333333333333",
            event_type="monitoring.alert.triggered",
            source_service="monitoring-service",
            payload={
                "affected_entity_type": "Learner",
                "affected_entity_id": "10101010-1010-1010-1010-101010101010",
                "alert_type_code": "LOW_ATTENDANCE",
            },
            trace_parent="00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
        )
    )

    notification, outbox = repo.saved_with_outbox[0]
    assert notification.send_status == SendStatus.SENT
    assert notification.channel == Channel.EMAIL
    assert notification.subject == "Alerta: LOW_ATTENDANCE"
    assert notification.template_id == "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    assert outbox.event_type == "notification.notification.sent"
    assert outbox.payload["notification_id"] == notification.id
    assert outbox.payload["trace_parent"].startswith("00-")


def test_consume_notifier_failure_marks_failed_and_skips_outbox():
    repo = FakeRepo()
    use_case = ConsumeDomainEvent(
        FakeResolver(), FakeNotifier(RuntimeError("smtp: connection refused")), repo, FakeTemplates()
    )
    use_case.handle(
        ConsumeDomainEventCommand(
            event_id="33333333-3333-3333-3333-333333333333",
            event_type="monitoring.alert.triggered",
            source_service="monitoring-service",
            payload={
                "affected_entity_type": "Learner",
                "affected_entity_id": "10101010-1010-1010-1010-101010101010",
                "alert_type_code": "LOW_ATTENDANCE",
            },
        )
    )
    notification, outbox = repo.saved_with_outbox[0]
    assert notification.send_status == SendStatus.FAILED
    assert "smtp" in notification.failure_reason
    assert outbox is None


def test_consume_schedule_published_notifies_published_by_only():
    repo = FakeRepo()
    resolver = FakeResolver(Recipient(id="99999999-9999-9999-9999-999999999999", email="publisher@sena.local"))
    use_case = ConsumeDomainEvent(resolver, FakeNotifier(), repo, FakeTemplates())
    use_case.handle(
        ConsumeDomainEventCommand(
            event_id="33333333-3333-3333-3333-333333333333",
            event_type="scheduling.schedule.published",
            source_service="scheduling-service",
            payload={"published_by": "99999999-9999-9999-9999-999999999999", "instructor_ids": ["other"]},
        )
    )
    notification, _ = repo.saved_with_outbox[0]
    assert notification.recipient_id == "99999999-9999-9999-9999-999999999999"


def test_consume_unsupported_event_is_rejected_before_persistence():
    repo = FakeRepo()
    use_case = ConsumeDomainEvent(FakeResolver(), FakeNotifier(), repo, FakeTemplates())
    with pytest.raises(ValueError, match="unsupported event_type"):
        use_case.handle(
            ConsumeDomainEventCommand(
                event_id="33333333-3333-3333-3333-333333333333",
                event_type="academic.enrollment_ficha.status_changed",
                source_service="academic",
                payload={},
            )
        )
    assert repo.saved_with_outbox == []
