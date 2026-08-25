from __future__ import annotations

import re
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, field_validator

EVENT_TYPE_PATTERN = re.compile(r"^[a-z_]+\.[a-z_]+\.[a-z_]+$")


class DomainEventEnvelope(BaseModel):
    model_config = ConfigDict(extra="ignore")

    correlation_id: str | None = None
    event_id: str
    event_type: str
    payload: dict[str, Any] | None
    source_service: str
    timestamp: datetime
    version: str

    @field_validator("event_type")
    @classmethod
    def event_type_must_match_contract(cls, value: str) -> str:
        if not EVENT_TYPE_PATTERN.fullmatch(value):
            raise ValueError("event_type must match ^[a-z_]+\\.[a-z_]+\\.[a-z_]+$")
        return value
