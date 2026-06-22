from enum import Enum

from pydantic import BaseModel, Field

from app.schemas.ticket import TicketStatus


class AiTicketActionRequest(BaseModel):
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used when reading ticket detail.",
    )


class ReplySuggestionResult(BaseModel):
    suggestion: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)


class SuggestedPriority(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class SlaRiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    UNKNOWN = "UNKNOWN"


class TicketSummaryResult(BaseModel):
    summary: str
    key_points: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class PrioritySuggestionResult(BaseModel):
    suggested_priority: SuggestedPriority
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)


class CategorySuggestionResult(BaseModel):
    suggested_category: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)


class SimilarTicketItem(BaseModel):
    id: int
    title: str
    status: TicketStatus
    similarity_reason: str


class SimilarTicketSearchResult(BaseModel):
    similar_tickets: list[SimilarTicketItem] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class SlaRiskResult(BaseModel):
    sla_risk_level: SlaRiskLevel
    reason: str
    missing_fields: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)
