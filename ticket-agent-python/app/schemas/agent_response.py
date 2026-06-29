from typing import Any

from pydantic import BaseModel, Field


class AgentResponse(BaseModel):
    type: str
    message: str
    data: Any | None = None
    risk_flags: list[str] = Field(default_factory=list)
    intent: str | None = None
    actionType: str | None = None
    riskLevel: str | None = None
    toolName: str | None = None
    targetType: str | None = None
    targetId: int | None = None
    requiresConfirmation: bool | None = None
    error: str | None = None
