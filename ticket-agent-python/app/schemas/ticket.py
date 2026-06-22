from enum import Enum
from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class TicketStatus(str, Enum):
    OPEN = "OPEN"
    PROCESSING = "PROCESSING"
    CLOSED = "CLOSED"


class TicketPriority(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    URGENT = "URGENT"


class TicketReplyType(str, Enum):
    USER = "USER"
    STAFF = "STAFF"
    AI = "AI"


class TicketDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str
    description: str | None = Field(
        default=None,
        validation_alias=AliasChoices("description", "content"),
    )
    status: TicketStatus
    priority: TicketPriority | None = None
    category: str | None = None
    createdBy: int | None = Field(
        default=None,
        validation_alias=AliasChoices("createdBy", "userId", "user_id"),
    )
    createdAt: str | None = None
    updatedAt: str | None = None
    deadline: str | None = None
    lastReplyAt: str | None = None
    responseDueAt: str | None = None
    resolveDueAt: str | None = None


class TicketReplyDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    ticketId: int
    userId: int | None = None
    content: str
    type: TicketReplyType = Field(
        validation_alias=AliasChoices("type", "replyType", "reply_type"),
    )
    createdAt: str | None = None
    updatedAt: str | None = None


class TicketDetailDTO(TicketDTO):
    replies: list[TicketReplyDTO] = Field(default_factory=list)
    user: dict[str, Any] | None = None

    @classmethod
    def from_java_detail(cls, data: Any) -> "TicketDetailDTO":
        if not isinstance(data, dict):
            raise ValueError("Java ticket detail data must be a dict")

        ticket = data.get("ticket")
        if not isinstance(ticket, dict):
            raise ValueError("Java ticket detail must contain ticket")

        payload = {
            **ticket,
            "replies": data.get("replies") or [],
            "user": data.get("user"),
        }
        return cls.model_validate(payload)


class CreateTicketRequest(BaseModel):
    title: str
    description: str
    priority: TicketPriority
    category: str | None = None

    def to_java_body(self) -> dict[str, Any]:
        body = {
            "title": self.title,
            "content": self.description,
            "priority": self.priority.value,
        }
        if self.category:
            body["category"] = self.category
        return body


class UpdateTicketStatusRequest(BaseModel):
    status: TicketStatus

    def to_java_body(self) -> dict[str, str]:
        return {"status": self.status.value}


class AiReplySaveRequest(BaseModel):
    content: str

    def to_java_body(self) -> dict[str, str]:
        return {"content": self.content}
