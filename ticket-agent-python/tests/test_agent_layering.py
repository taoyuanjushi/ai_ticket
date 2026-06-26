import asyncio
from typing import Any

from fastapi.testclient import TestClient
import pytest

from app.api import agent_api
from app.main import app
from app.schemas.agent_response import AgentResponse
from app.schemas.agent_schema import AgentChatRequest
from app.schemas.intent_schema import IntentResult, IntentType
from app.schemas.ticket_ai_schema import (
    CategorySuggestionResult,
    PrioritySuggestionResult,
    ReplySuggestionResult,
    SimilarTicketSearchResult,
    SlaRiskResult,
    TicketSummaryResult,
)
from app.services.agent_tool_service import AgentToolService
from app.services.guardrail_service import GuardrailService
from app.services.response_builder import ResponseBuilder

client = TestClient(app)


def test_agent_api_delegates_to_agent_tool_service_handle(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeAgentToolService:
        def __init__(self) -> None:
            self.request: AgentChatRequest | None = None

        async def handle(self, request: AgentChatRequest) -> AgentResponse:
            self.request = request
            return ResponseBuilder.normal("service ok")

    fake_service = FakeAgentToolService()
    monkeypatch.setattr(agent_api, "agent_tool_service", fake_service)

    response = client.post(
        "/agent/chat",
        json={
            "message": "hello",
            "user_id": "7",
            "conversation_id": "conv-1",
            "auth_token": "java-token",
        },
    )

    assert response.status_code == 200
    assert response.json()["type"] == "NORMAL"
    assert response.json()["answer"] == "service ok"
    assert fake_service.request is not None
    assert fake_service.request.message == "hello"
    assert fake_service.request.conversation_id == "conv-1"


def test_agent_api_returns_readable_error_for_unexpected_service_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingAgentToolService:
        async def handle(self, request: AgentChatRequest) -> AgentResponse:
            raise RuntimeError("boom")

    monkeypatch.setattr(agent_api, "agent_tool_service", FailingAgentToolService())

    response = client.post("/agent/chat", json={"message": "hello"})

    assert response.status_code == 200
    assert response.json()["type"] == "ERROR"
    assert response.json()["answer"] == "AI 服务暂时异常，请稍后重试。"
    assert response.json()["risk_flags"] == ["Agent服务异常"]


def test_agent_tool_service_handle_returns_typed_agent_response() -> None:
    service = AgentToolService(intent_recognizer=StaticIntentRecognizer(IntentType.UNKNOWN))

    response = asyncio.run(service.handle(AgentChatRequest(message="unknown")))

    assert isinstance(response, AgentResponse)
    assert response.type == "UNKNOWN_INTENT"
    assert response.message


@pytest.mark.parametrize(
    ("intent", "expected_call", "expected_data_key"),
    [
        (IntentType.REPLY_SUGGESTION, "generate_reply_suggestion_by_ticket_id", "suggestion"),
        (IntentType.TICKET_SUMMARY, "generate_ticket_summary_by_ticket_id", "summary"),
        (IntentType.PRIORITY_SUGGESTION, "suggest_priority_by_ticket_id", "suggested_priority"),
        (IntentType.CATEGORY_SUGGESTION, "suggest_category_by_ticket_id", "suggested_category"),
        (IntentType.SIMILAR_TICKET_SEARCH, "search_similar_tickets_by_ticket_id", "similar_tickets"),
        (IntentType.SLA_RISK_CHECK, "check_sla_risk_by_ticket_id", "sla_risk_level"),
    ],
)
def test_agent_tool_service_dispatches_to_ticket_ai_capability_services(
    intent: IntentType,
    expected_call: str,
    expected_data_key: str,
) -> None:
    ticket_ai_service = FakeTicketAiService()
    service = AgentToolService(
        intent_recognizer=StaticIntentRecognizer(intent, ticket_id=3),
        ticket_ai_service=ticket_ai_service,
    )

    response = asyncio.run(
        service.handle(AgentChatRequest(message="run capability", auth_token="java-token"))
    )

    assert response.type == "JSON_RESULT"
    assert expected_data_key in response.data
    assert ticket_ai_service.calls == [(expected_call, 3, "java-token")]


def test_guardrail_service_only_checks_output_against_provided_context() -> None:
    service = GuardrailService()

    unsupported = service.find_unsupported_high_risk_text(
        output_text="logs show the issue is fixed",
        ticket_context={"description": "user cannot login", "token": "secret"},
        high_risk_phrases=["logs show", "fixed"],
    )
    supported = service.find_unsupported_high_risk_text(
        output_text="logs show the issue is still open",
        ticket_context={"description": "logs show the issue is still open"},
        high_risk_phrases=["logs show"],
    )

    assert unsupported == ["logs show", "fixed"]
    assert supported == []


class StaticIntentRecognizer:
    def __init__(self, intent: IntentType, ticket_id: int | None = None) -> None:
        self.intent = intent
        self.ticket_id = ticket_id

    def recognize(self, message: str) -> IntentResult:
        missing_fields = [] if self.ticket_id is not None or self.intent == IntentType.UNKNOWN else ["ticket_id"]
        return IntentResult(
            intent=self.intent,
            ticket_id=self.ticket_id,
            confidence=1.0,
            missing_fields=missing_fields,
            raw_message=message,
        )


class FakeTicketAiService:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int, str | None]] = []

    def generate_reply_suggestion_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> ReplySuggestionResult:
        self._record("generate_reply_suggestion_by_ticket_id", ticket_id, auth_token)
        return ReplySuggestionResult(
            suggestion="reply",
            confidence=0.8,
            reason="ticket detail",
            risk_flags=[],
        )

    def generate_ticket_summary_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> TicketSummaryResult:
        self._record("generate_ticket_summary_by_ticket_id", ticket_id, auth_token)
        return TicketSummaryResult(summary="summary", key_points=["open"], risk_flags=[])

    def suggest_priority_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> PrioritySuggestionResult:
        self._record("suggest_priority_by_ticket_id", ticket_id, auth_token)
        return PrioritySuggestionResult(
            suggested_priority="HIGH",
            confidence=0.7,
            reason="impact",
            risk_flags=[],
        )

    def suggest_category_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> CategorySuggestionResult:
        self._record("suggest_category_by_ticket_id", ticket_id, auth_token)
        return CategorySuggestionResult(
            suggested_category="login",
            confidence=0.7,
            reason="keyword",
            risk_flags=[],
        )

    def search_similar_tickets_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> SimilarTicketSearchResult:
        self._record("search_similar_tickets_by_ticket_id", ticket_id, auth_token)
        return SimilarTicketSearchResult(similar_tickets=[], risk_flags=[])

    def check_sla_risk_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> SlaRiskResult:
        self._record("check_sla_risk_by_ticket_id", ticket_id, auth_token)
        return SlaRiskResult(
            sla_risk_level="UNKNOWN",
            reason="missing fields",
            missing_fields=["resolveDueAt"],
            risk_flags=["SLA fields missing"],
        )

    def _record(self, method: str, ticket_id: int, auth_token: str | None) -> None:
        self.calls.append((method, ticket_id, auth_token))
