from pydantic import BaseModel, Field

from app.schemas.ai_outputs import (
    CategorySuggestionData,
    CategorySuggestionResult,
    PrioritySuggestionData,
    PrioritySuggestionResult,
    ReplySuggestionData,
    ReplySuggestionResult,
    SimilarTicketItem,
    SimilarTicketSearchResult,
    SimilarTicketsData,
    SlaRiskData,
    SlaRiskLevel,
    SlaRiskResult,
    SuggestedPriority,
    TicketSummaryData,
    TicketSummaryResult,
)


class AiTicketActionRequest(BaseModel):
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used when reading ticket detail.",
    )


__all__ = [
    "AiTicketActionRequest",
    "ReplySuggestionData",
    "ReplySuggestionResult",
    "TicketSummaryData",
    "TicketSummaryResult",
    "PrioritySuggestionData",
    "PrioritySuggestionResult",
    "CategorySuggestionData",
    "CategorySuggestionResult",
    "SimilarTicketItem",
    "SimilarTicketsData",
    "SimilarTicketSearchResult",
    "SlaRiskData",
    "SlaRiskResult",
    "SuggestedPriority",
    "SlaRiskLevel",
]
