from typing import Any

from pydantic import BaseModel, Field, field_validator


class AgentChatRequest(BaseModel):
    message: str = Field(..., min_length=1)
    user_id: str | None = Field(
        default=None,
        description="Optional user id used to isolate pending actions.",
    )
    conversation_id: str | None = Field(
        default=None,
        description="Optional conversation id used to isolate pending actions.",
    )
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used when tools call the Java API.",
    )

    @field_validator("message")
    @classmethod
    def validate_message(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("message 不能为空")
        return value

    @field_validator("user_id", "conversation_id", mode="before")
    @classmethod
    def normalize_optional_session_field(cls, value: Any) -> Any:
        if value is None:
            return None
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)):
            value = str(value)
        if isinstance(value, str):
            value = value.strip()
            return value or None
        return value


class AgentChatResponse(BaseModel):
    answer: str
    type: str = "NORMAL"
    message: str | None = None
    data: Any | None = None
    risk_flags: list[str] = Field(default_factory=list)
