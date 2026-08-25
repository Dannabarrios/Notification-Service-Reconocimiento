from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import Any


class Channel(StrEnum):
    EMAIL = "EMAIL"
    IN_APP = "IN_APP"


class SendStatus(StrEnum):
    PENDING = "PENDING"
    SENT = "SENT"
    FAILED = "FAILED"


@dataclass(slots=True)
class SentNotification:
    id: str = ""
    recipient_id: str = ""
    recipient_email: str = ""
    channel: Channel = Channel.EMAIL
    subject: str = ""
    body_summary: str = ""
    send_status: SendStatus = SendStatus.PENDING
    failure_reason: str = ""
    template_id: str = ""
    source_service: str = ""
    source_event_id: str = ""
    sent_at: datetime | None = None
    created_at: datetime | None = None


@dataclass(slots=True)
class NotificationTemplate:
    id: str
    code: str
    channel: Channel
    subject_template: str
    body_template: str
    is_active: bool
    created_at: datetime | None = None
    updated_at: datetime | None = None


@dataclass(slots=True)
class Recipient:
    id: str
    email: str


@dataclass(slots=True)
class OutboxEvent:
    id: str
    event_id: str
    event_type: str
    payload: dict[str, Any]
    created_at: datetime
    published_at: datetime | None = None


class NotificationNotFoundError(Exception):
    pass
