from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class AiPendingActionType(str, Enum):
    CREATE_TICKET = "CREATE_TICKET"
    UPDATE_TICKET_STATUS = "UPDATE_TICKET_STATUS"
    SAVE_AI_REPLY = "SAVE_AI_REPLY"
    APPLY_AI_CATEGORY = "APPLY_AI_CATEGORY"


class AiPendingActionStatus(str, Enum):
    PENDING = "PENDING"
    CONFIRMED = "CONFIRMED"
    CANCELLED = "CANCELLED"
    EXPIRED = "EXPIRED"


class CreateAiPendingActionRequest(BaseModel):
    conversationId: str
    actionType: AiPendingActionType
    payload: dict[str, Any]


class AiPendingActionDTO(BaseModel):
    id: int
    userId: int
    conversationId: str
    actionType: AiPendingActionType
    payload: dict[str, Any]
    status: AiPendingActionStatus
    createdAt: str | None = None
    updatedAt: str | None = None
    confirmedAt: str | None = None
    cancelledAt: str | None = None


class AiPendingActionConfirmResult(BaseModel):
    pendingAction: AiPendingActionDTO
    result: Any = Field(default=None)
