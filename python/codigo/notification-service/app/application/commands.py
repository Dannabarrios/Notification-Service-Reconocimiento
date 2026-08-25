from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from app.domain.models import Channel


@dataclass(slots=True)
class SendNotificationCommand:
    recipient_id: str
    recipient_email: str
    channel: Channel
    subject: str
    template_code: str = ""
    template_vars: dict[str, str] = field(default_factory=dict)
    source_service: str = ""
    source_event_id: str = ""


@dataclass(slots=True)
class GetNotificationQuery:
    id: str


@dataclass(slots=True)
class ConsumeDomainEventCommand:
    event_id: str
    event_type: str
    source_service: str
    payload: dict[str, Any]
    trace_parent: str = ""
