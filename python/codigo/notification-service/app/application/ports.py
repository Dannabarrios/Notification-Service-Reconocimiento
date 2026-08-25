from __future__ import annotations

from typing import Protocol

from app.domain.models import NotificationTemplate, OutboxEvent, Recipient, SentNotification


class NotificationRepository(Protocol):
    def save(self, notification: SentNotification) -> None: ...
    def save_with_outbox(self, notification: SentNotification, event: OutboxEvent | None) -> bool: ...
    def find_by_id(self, notification_id: str) -> SentNotification | None: ...
    def ping(self) -> None: ...


class TemplateRepository(Protocol):
    def find_by_code(self, code: str) -> NotificationTemplate | None: ...


class RecipientResolver(Protocol):
    def resolve(self, entity_type: str, entity_id: str) -> Recipient: ...


class Notifier(Protocol):
    def send(self, notification: SentNotification) -> None: ...
