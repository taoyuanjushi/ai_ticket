from fastapi import APIRouter

from app.schemas.ticket_ai_schema import (
    AiTicketActionRequest,
    CategorySuggestionResult,
    PrioritySuggestionResult,
    ReplySuggestionResult,
    SimilarTicketSearchResult,
    SlaRiskResult,
    TicketSummaryResult,
)
from app.services.ticket_ai_service import TicketAiService

router = APIRouter(tags=["Ticket AI"])
ticket_ai_service = TicketAiService()


@router.post(
    "/ai/tickets/{ticket_id}/reply-suggestion",
    response_model=ReplySuggestionResult,
)
async def generate_reply_suggestion(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> ReplySuggestionResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.generate_reply_suggestion_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )


@router.post(
    "/ai/tickets/{ticket_id}/summary",
    response_model=TicketSummaryResult,
)
async def generate_ticket_summary(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> TicketSummaryResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.generate_ticket_summary_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )


@router.post(
    "/ai/tickets/{ticket_id}/priority-suggestion",
    response_model=PrioritySuggestionResult,
)
async def suggest_ticket_priority(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> PrioritySuggestionResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.suggest_priority_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )


@router.post(
    "/ai/tickets/{ticket_id}/category-suggestion",
    response_model=CategorySuggestionResult,
)
async def suggest_ticket_category(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> CategorySuggestionResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.suggest_category_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )


@router.post(
    "/ai/tickets/{ticket_id}/similar-tickets",
    response_model=SimilarTicketSearchResult,
)
async def search_similar_tickets(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> SimilarTicketSearchResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.search_similar_tickets_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )


@router.post(
    "/ai/tickets/{ticket_id}/sla-risk",
    response_model=SlaRiskResult,
)
async def check_ticket_sla_risk(
    ticket_id: int,
    request: AiTicketActionRequest | None = None,
) -> SlaRiskResult:
    auth_token = request.auth_token if request else None
    return ticket_ai_service.check_sla_risk_by_ticket_id(
        ticket_id=ticket_id,
        auth_token=auth_token,
    )
