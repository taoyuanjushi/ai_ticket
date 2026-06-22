from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class IntentType(str, Enum):
    QUERY_TICKET = "QUERY_TICKET"
    CREATE_TICKET = "CREATE_TICKET"
    UPDATE_TICKET_STATUS = "UPDATE_TICKET_STATUS"
    REPLY_SUGGESTION = "REPLY_SUGGESTION"
    SAVE_AI_REPLY = "SAVE_AI_REPLY"
    CONFIRM = "CONFIRM"
    CANCEL = "CANCEL"
    TICKET_SUMMARY = "TICKET_SUMMARY"
    PRIORITY_SUGGESTION = "PRIORITY_SUGGESTION"
    CATEGORY_SUGGESTION = "CATEGORY_SUGGESTION"
    SIMILAR_TICKET_SEARCH = "SIMILAR_TICKET_SEARCH"
    SLA_RISK_CHECK = "SLA_RISK_CHECK"
    UNKNOWN = "UNKNOWN"


class IntentResult(BaseModel):
    intent: IntentType
    ticket_id: Optional[int] = None
    title: Optional[str] = None
    description: Optional[str] = None
    priority: Optional[str] = None
    target_status: Optional[str] = None
    keyword: Optional[str] = None
    reply_content: Optional[str] = None
    confidence: float = 0.0
    missing_fields: list[str] = Field(default_factory=list)
    raw_message: str
