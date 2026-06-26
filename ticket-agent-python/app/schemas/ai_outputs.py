from enum import Enum

from pydantic import BaseModel, Field, field_validator


class ReplySuggestionData(BaseModel):
    suggestion: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)


class TicketSummaryData(BaseModel):
    summary: str
    key_points: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class SuggestedPriority(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class PrioritySuggestionData(BaseModel):
    suggested_priority: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)

    @field_validator("suggested_priority")
    @classmethod
    def validate_priority(cls, value: str | SuggestedPriority) -> str:
        upper_value = str(value.value if isinstance(value, SuggestedPriority) else value).upper()
        allowed = {item.value for item in SuggestedPriority}
        if upper_value not in allowed:
            raise ValueError(f"suggested_priority must be one of {sorted(allowed)}")
        return upper_value


class CategorySuggestionData(BaseModel):
    suggested_category: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    risk_flags: list[str] = Field(default_factory=list)


class SimilarTicketItem(BaseModel):
    id: int
    title: str
    status: str | None = None
    similarity_reason: str


class SimilarTicketsData(BaseModel):
    similar_tickets: list[SimilarTicketItem] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class SlaRiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    UNKNOWN = "UNKNOWN"


class SlaRiskData(BaseModel):
    sla_risk_level: str
    reason: str
    missing_fields: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)

    @field_validator("sla_risk_level")
    @classmethod
    def validate_sla_risk_level(cls, value: str | SlaRiskLevel) -> str:
        upper_value = str(value.value if isinstance(value, SlaRiskLevel) else value).upper()
        allowed = {item.value for item in SlaRiskLevel}
        if upper_value not in allowed:
            raise ValueError(f"sla_risk_level must be one of {sorted(allowed)}")
        return upper_value


# Backward-compatible aliases for the existing service and API imports.
ReplySuggestionResult = ReplySuggestionData
TicketSummaryResult = TicketSummaryData
PrioritySuggestionResult = PrioritySuggestionData
CategorySuggestionResult = CategorySuggestionData
SimilarTicketSearchResult = SimilarTicketsData
SlaRiskResult = SlaRiskData
