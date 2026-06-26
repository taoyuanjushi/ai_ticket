from __future__ import annotations

from app.clients.java_ticket_client import JavaApiError, JavaTicketClient
from app.core.exceptions import AppException, JAVA_API_ERROR
from app.schemas.ticket import TicketDTO, TicketDetailDTO
from app.services.grounding import (
    HUMAN_REVIEW_FLAG,
    INSUFFICIENT_INFORMATION_FLAG,
    TicketGroundingService,
)


class TicketAiCapabilityBase:
    def __init__(
        self,
        java_ticket_client: JavaTicketClient | None = None,
        grounding_service: TicketGroundingService | None = None,
    ) -> None:
        self.java_ticket_client = java_ticket_client
        self.grounding_service = grounding_service or TicketGroundingService()

    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        try:
            java_ticket_client = self.java_ticket_client or JavaTicketClient()
            return java_ticket_client.get_ticket_detail(
                auth_token=auth_token,
                ticket_id=ticket_id,
            )
        except JavaApiError as exc:
            raise AppException(
                code=JAVA_API_ERROR,
                message=exc.message,
                status_code=exc.status_code,
                detail={
                    "risk_flags": self.grounding_service.java_error_risk_flags(
                        exc.status_code
                    )
                },
            ) from exc

    def search_tickets(
        self,
        auth_token: str | None,
        query: dict,
    ) -> list[TicketDTO]:
        try:
            java_ticket_client = self.java_ticket_client or JavaTicketClient()
            if hasattr(java_ticket_client, "list_tickets"):
                return java_ticket_client.list_tickets(
                    auth_token=auth_token,
                    query=query,
                )
            return java_ticket_client.search_tickets(auth_token=auth_token, query=query)
        except JavaApiError as exc:
            raise AppException(
                code=JAVA_API_ERROR,
                message=exc.message,
                status_code=exc.status_code,
                detail={
                    "risk_flags": self.grounding_service.java_error_risk_flags(
                        exc.status_code
                    )
                },
            ) from exc

    def base_risk_flags(self, ticket_detail: TicketDetailDTO) -> list[str]:
        if self.grounding_service.is_information_insufficient(ticket_detail):
            return [INSUFFICIENT_INFORMATION_FLAG, HUMAN_REVIEW_FLAG]
        return []

    def merge_risk_flags(
        self,
        existing_flags: list[str],
        new_flags: list[str],
    ) -> list[str]:
        merged = list(existing_flags)
        for flag in new_flags:
            if flag not in merged:
                merged.append(flag)
        return merged

    def text_for_analysis(self, ticket_detail: TicketDetailDTO) -> str:
        reply_text = " ".join(reply.content for reply in ticket_detail.replies)
        return " ".join(
            value
            for value in (
                ticket_detail.title,
                ticket_detail.description or "",
                ticket_detail.category or "",
                reply_text,
            )
            if value
        )
