from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

from app.application.commands import ConsumeDomainEventCommand, GetNotificationQuery, SendNotificationCommand
from app.domain.models import (
    Channel,
    NotificationNotFoundError,
    OutboxEvent,
    SendStatus,
    SentNotification,
)
from app.domain.services import render_template

OUTBOX_EVENT_TYPE_NOTIFICATION_SENT = "notification.notification.sent"


class SendNotification:
    def __init__(self, repository, template_repository):
        self.repository = repository
        self.template_repository = template_repository

    def handle(self, command: SendNotificationCommand) -> SentNotification:
        notification = SentNotification(
            recipient_id=command.recipient_id,
            recipient_email=command.recipient_email,
            channel=command.channel,
            subject=command.subject,
            send_status=SendStatus.PENDING,
            source_service=command.source_service,
            source_event_id=command.source_event_id,
            created_at=datetime.now(UTC),
        )
        if command.template_code:
            try:
                template = self.template_repository.find_by_code(command.template_code)
            except Exception:
                template = None
            if template is not None and template.is_active:
                notification.subject = render_template(template.subject_template, command.template_vars)
                notification.body_summary = render_template(template.body_template, command.template_vars)
                notification.template_id = template.id
        self.repository.save(notification)
        return notification


class GetNotification:
    def __init__(self, repository):
        self.repository = repository

    def handle(self, query: GetNotificationQuery) -> SentNotification:
        notification = self.repository.find_by_id(query.id)
        if notification is None:
            raise NotificationNotFoundError("notification not found")
        return notification


def _recipient_ref(command: ConsumeDomainEventCommand) -> tuple[str, str, str]:
    if command.event_type == "monitoring.alert.triggered":
        entity_type = command.payload.get("affected_entity_type")
        entity_id = command.payload.get("affected_entity_id")
        if not isinstance(entity_type, str) or not entity_type or not isinstance(entity_id, str) or not entity_id:
            raise ValueError("monitoring.alert.triggered payload missing affected_entity_type/affected_entity_id")
        alert_type = command.payload.get("alert_type_code")
        alert_type = alert_type if isinstance(alert_type, str) else ""
        return entity_type, entity_id, f"Alert triggered: {alert_type}"
    if command.event_type == "scheduling.schedule.published":
        published_by = command.payload.get("published_by")
        if not isinstance(published_by, str) or not published_by:
            raise ValueError("scheduling.schedule.published payload missing published_by")
        return "Instructor", published_by, "Schedule published"
    raise ValueError(f"unsupported event_type: {command.event_type}")


def _template_code(event_type: str) -> str:
    return {
        "monitoring.alert.triggered": "ALERT_TRIGGERED",
        "scheduling.schedule.published": "SCHEDULE_PUBLISHED",
    }.get(event_type, "")


def _string(value) -> str:
    return value if isinstance(value, str) else ""


def _template_vars(event_type: str, payload: dict) -> dict[str, str]:
    if event_type == "monitoring.alert.triggered":
        return {
            "alert_type": _string(payload.get("alert_type_code")),
            "ficha": _string(payload.get("affected_entity_id")),
        }
    if event_type == "scheduling.schedule.published":
        return {
            "schedule_name": _string(payload.get("schedule_name")),
            "ficha": _string(payload.get("ficha")),
        }
    return {}


class ConsumeDomainEvent:
    def __init__(self, recipient_resolver, notifier, repository, template_repository):
        self.recipient_resolver = recipient_resolver
        self.notifier = notifier
        self.repository = repository
        self.template_repository = template_repository

    def handle(self, command: ConsumeDomainEventCommand) -> None:
        entity_type, entity_id, subject = _recipient_ref(command)
        recipient = self.recipient_resolver.resolve(entity_type, entity_id)
        now = datetime.now(UTC)
        notification = SentNotification(
            id=str(uuid4()),
            recipient_id=recipient.id,
            recipient_email=recipient.email,
            channel=Channel.EMAIL,
            subject=subject,
            source_service=command.source_service,
            source_event_id=command.event_id,
            created_at=now,
        )

        code = _template_code(command.event_type)
        if code:
            try:
                template = self.template_repository.find_by_code(code)
            except Exception:
                template = None
            if template is not None and template.is_active:
                variables = _template_vars(command.event_type, command.payload)
                notification.subject = render_template(template.subject_template, variables)
                notification.body_summary = render_template(template.body_template, variables)
                notification.template_id = template.id

        try:
            self.notifier.send(notification)
        except Exception as exc:
            notification.send_status = SendStatus.FAILED
            notification.failure_reason = str(exc)
        else:
            notification.send_status = SendStatus.SENT
            notification.sent_at = datetime.now(UTC)

        outbox_event = None
        if notification.send_status == SendStatus.SENT:
            outbox_event = OutboxEvent(
                id=str(uuid4()),
                event_id=str(uuid4()),
                event_type=OUTBOX_EVENT_TYPE_NOTIFICATION_SENT,
                payload={
                    "notification_id": notification.id,
                    "recipient_id": notification.recipient_id,
                    "channel": notification.channel.value,
                    "sent_at": notification.sent_at.isoformat().replace("+00:00", "Z")
                    if notification.sent_at
                    else None,
                    **({"trace_parent": command.trace_parent} if command.trace_parent else {}),
                },
                created_at=now,
            )

        self.repository.save_with_outbox(notification, outbox_event)
