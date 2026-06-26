from datetime import datetime, timezone
from typing import Any

from pydantic import BaseModel, Field

from app.schemas.intent_schema import IntentType


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class PendingIntent(BaseModel):
    """Temporary intent-parameter collection state, not a write confirmation."""

    user_id: str
    conversation_id: str
    intent: IntentType
    collected: dict[str, Any] = Field(default_factory=dict)
    missing_fields: list[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=utc_now)
    updated_at: datetime = Field(default_factory=utc_now)
