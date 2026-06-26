from typing import Any

from pydantic import BaseModel, Field


class AgentResponse(BaseModel):
    type: str
    message: str
    data: Any | None = None
    risk_flags: list[str] = Field(default_factory=list)
