import time
from typing import Any, Literal

from pydantic import BaseModel, Field


class PendingAction(BaseModel):
    tool_name: Literal["create_ticket", "update_ticket_status", "save_ai_reply"] = Field(
        description="Tool name to execute after user approval.",
    )
    args: dict[str, Any] = Field(
        description="Arguments passed to the tool after user approval.",
    )
    summary: str = Field(
        description="Human-readable summary shown to the user before approval.",
    )
    action_type: Literal["write"] = Field(
        default="write",
        description="Action type. Current pending actions are write operations.",
    )
    created_at: float = Field(
        default_factory=time.time,
        description="Unix timestamp when this pending action was created.",
    )
