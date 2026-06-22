from typing import Any

import pytest

from app.clients.java_ticket_client import JavaApiError
from app.schemas.pending_action import (
    AiPendingActionConfirmResult,
    AiPendingActionDTO,
    AiPendingActionStatus,
    AiPendingActionType,
    CreateAiPendingActionRequest,
)
from app.schemas.ticket import (
    CreateTicketRequest,
    TicketReplyDTO,
    TicketDTO,
    TicketDetailDTO,
    UpdateTicketStatusRequest,
)
from app.api import agent_api
from app.services import agent_tool_service
from app.services import ticket_ai_service
from app.services.ai_capabilities import base as ai_capability_base
from app.tools import ticket_tools
from app.tools.mock_ticket_data import (
    MOCK_TICKETS,
    append_mock_ticket,
    get_next_ticket_id,
    reset_mock_tickets,
)


class FakeJavaTicketClient:
    LOGIN_EXPIRED_MESSAGE = "登录状态已失效，请重新登录。"
    TOKEN_USER_IDS: dict[str, int] = {
        "A_TOKEN": 1,
        "A_TOKEN_NEW": 1,
        "B_TOKEN": 2,
        "B_TOKEN_NEW": 2,
        "java-token": 7,
        "STAFF_TOKEN": 7,
        "test-token": 9,
    }
    seen_tokens: list[str | None] = []
    pending_actions: dict[tuple[str, str], dict[str, Any]] = {}
    next_pending_action_id: int = 1
    confirm_counts: dict[tuple[str, str], int] = {}

    def __init__(
        self,
        base_url: str | None = None,
        token: str | None = None,
        timeout: float | None = None,
    ) -> None:
        self.base_url = base_url
        self.token = token
        self.timeout = timeout
        self.seen_tokens.append(token)

    def search_tickets(
        self,
        auth_token: str | None = None,
        query: dict[str, Any] | None = None,
    ) -> list[TicketDTO]:
        self.seen_tokens.append(auth_token)
        query = query or {}
        status = query.get("status")
        priority = query.get("priority")
        category = query.get("category")
        keyword = query.get("keyword")
        page = int(query.get("page") or 1)
        size = int(query.get("size") or 10)
        records = []
        normalized_keyword = keyword.strip().lower() if keyword else None
        for ticket in MOCK_TICKETS:
            if status and ticket["status"] != status:
                continue
            if priority and ticket["priority"] != priority:
                continue
            if category and ticket.get("category") != category:
                continue
            if normalized_keyword and not self._contains_keyword(ticket, normalized_keyword):
                continue
            records.append(self._to_java_ticket(ticket))

        start = (page - 1) * size
        end = start + size
        return [TicketDTO.model_validate(record) for record in records[start:end]]

    def get_ticket_by_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> TicketDTO:
        self.seen_tokens.append(auth_token)
        ticket = self._find(ticket_id)
        if ticket is None:
            raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")
        return TicketDTO.model_validate(self._to_java_ticket(ticket))

    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        self.seen_tokens.append(auth_token)
        ticket = self._find(ticket_id)
        if ticket is None:
            raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")
        return TicketDetailDTO.from_java_detail({
            "ticket": self._to_java_ticket(ticket),
            "user": {
                "id": ticket.get("userId", 1),
                "username": "tom",
                "role": "USER",
            },
            "replies": [
                {
                    "id": 1,
                    "ticketId": ticket_id,
                    "content": "已收到问题，正在排查。",
                    "replyType": "STAFF",
                    "createdAt": "2026-06-16 10:00:00",
                }
            ],
        })

    def create_ticket(
        self,
        auth_token: str | None,
        req: CreateTicketRequest,
    ) -> TicketDTO:
        self.seen_tokens.append(auth_token)
        ticket = {
            "id": get_next_ticket_id(),
            "title": req.title,
            "description": req.description,
            "priority": req.priority.value,
            "status": "OPEN",
            "category": req.category or "OTHER",
        }
        append_mock_ticket(ticket)
        return TicketDTO.model_validate(self._to_java_ticket(ticket))

    def update_ticket_status(
        self,
        auth_token: str | None,
        ticket_id: int,
        req: UpdateTicketStatusRequest,
    ) -> TicketDTO:
        self.seen_tokens.append(auth_token)
        status = req.status.value
        ticket = self._find(ticket_id)
        if ticket is None:
            raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")
        if ticket["status"] == status:
            raise JavaApiError(400, f"工单已经是 {status} 状态，无需重复修改。")
        if not self._is_valid_transition(ticket["status"], status):
            raise JavaApiError(400, "工单状态流转不合法")

        ticket["status"] = status
        return TicketDTO.model_validate(self._to_java_ticket(ticket))

    def save_ai_reply(
        self,
        auth_token: str | None,
        ticket_id: int,
        req: Any,
    ) -> TicketReplyDTO:
        self.seen_tokens.append(auth_token)
        if self._find(ticket_id) is None:
            raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")
        return TicketReplyDTO.model_validate(
            {
                "id": 100 + ticket_id,
                "ticketId": ticket_id,
                "userId": 2,
                "content": req.content,
                "replyType": "AI",
            }
        )

    def create_pending_action(
        self,
        auth_token: str | None,
        req: CreateAiPendingActionRequest,
    ) -> AiPendingActionDTO:
        self.seen_tokens.append(auth_token)
        user_id = self._require_user_id(auth_token)
        self._assert_payload_has_no_token(req.payload)
        key = self._pending_key(auth_token, req.conversationId)
        action = {
            "id": self._next_pending_id(),
            "userId": user_id,
            "conversationId": req.conversationId,
            "actionType": req.actionType.value,
            "payload": req.payload.copy(),
            "status": AiPendingActionStatus.PENDING.value,
            "createdAt": "2026-06-17T10:00:00",
            "updatedAt": "2026-06-17T10:00:00",
        }
        self.pending_actions[key] = action
        return AiPendingActionDTO.model_validate(action)

    def get_current_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionDTO | None:
        self.seen_tokens.append(auth_token)
        self._require_user_id(auth_token)
        action = self.pending_actions.get(self._pending_key(auth_token, conversation_id))
        if action is None or action["status"] != AiPendingActionStatus.PENDING.value:
            return None
        return AiPendingActionDTO.model_validate(action)

    def confirm_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionConfirmResult:
        self.seen_tokens.append(auth_token)
        self._require_user_id(auth_token)
        key = self._pending_key(auth_token, conversation_id)
        action = self.pending_actions.get(key)
        if action is None or action["status"] != AiPendingActionStatus.PENDING.value:
            raise JavaApiError(400, "当前没有待确认的操作，请重新发起请求。")

        action["status"] = AiPendingActionStatus.CONFIRMED.value
        action["confirmedAt"] = "2026-06-17T10:01:00"
        self.confirm_counts[key] = self.confirm_counts.get(key, 0) + 1
        result = self._execute_pending_action(auth_token, action)
        return AiPendingActionConfirmResult.model_validate(
            {
                "pendingAction": action,
                "result": result,
            }
        )

    def cancel_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionDTO:
        self.seen_tokens.append(auth_token)
        self._require_user_id(auth_token)
        key = self._pending_key(auth_token, conversation_id)
        action = self.pending_actions.get(key)
        if action is None or action["status"] != AiPendingActionStatus.PENDING.value:
            raise JavaApiError(400, "当前会话没有待取消的操作。")

        action["status"] = AiPendingActionStatus.CANCELLED.value
        action["cancelledAt"] = "2026-06-17T10:01:00"
        return AiPendingActionDTO.model_validate(action)

    def _find(self, ticket_id: int) -> dict[str, Any] | None:
        for ticket in MOCK_TICKETS:
            if ticket["id"] == ticket_id:
                return ticket
        return None

    def _contains_keyword(self, ticket: dict[str, Any], keyword: str) -> bool:
        return keyword in ticket.get("title", "").lower() or keyword in ticket.get(
            "description", ""
        ).lower()

    def _is_valid_transition(self, old_status: str, new_status: str) -> bool:
        allowed_transitions = {
            "OPEN": ["PROCESSING", "CLOSED"],
            "PROCESSING": ["CLOSED"],
            "CLOSED": [],
        }
        return new_status in allowed_transitions.get(old_status, [])

    def _to_java_ticket(self, ticket: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": ticket["id"],
            "title": ticket["title"],
            "content": ticket.get("description", ticket.get("content", "")),
            "status": ticket["status"],
            "priority": ticket["priority"],
            "category": ticket.get("category", "OTHER"),
            "userId": ticket.get("userId", 1),
        }

    def _pending_key(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> tuple[str, str]:
        return (str(self._require_user_id(auth_token)), conversation_id)

    def _require_user_id(self, auth_token: str | None) -> int:
        token = self._normalize_token(auth_token)
        if token is None or token in {"TAMPERED_TOKEN", "EXPIRED_TOKEN"}:
            raise JavaApiError(401, self.LOGIN_EXPIRED_MESSAGE)
        user_id = self.TOKEN_USER_IDS.get(token)
        if user_id is None:
            raise JavaApiError(401, self.LOGIN_EXPIRED_MESSAGE)
        return user_id

    def _normalize_token(self, auth_token: str | None) -> str | None:
        if auth_token is None:
            return None
        token = auth_token.strip()
        if not token:
            return None
        if token.startswith("Bearer "):
            token = token[7:].strip()
        return token or None

    def _next_pending_id(self) -> int:
        next_id = self.__class__.next_pending_action_id
        self.__class__.next_pending_action_id += 1
        return next_id

    def _assert_payload_has_no_token(self, payload: dict[str, Any]) -> None:
        assert "auth_token" not in payload
        assert "token" not in payload
        assert "Authorization" not in payload
        assert "authorization" not in payload

    def _execute_pending_action(
        self,
        auth_token: str | None,
        action: dict[str, Any],
    ) -> dict[str, Any]:
        action_type = action["actionType"]
        payload = action["payload"]
        if action_type == AiPendingActionType.CREATE_TICKET.value:
            ticket = self.create_ticket(
                auth_token=auth_token,
                req=CreateTicketRequest(
                    title=payload["title"],
                    description=payload["content"],
                    priority=payload["priority"],
                    category=payload.get("category"),
                ),
            )
            return ticket.model_dump(mode="json", exclude_none=True)

        if action_type == AiPendingActionType.UPDATE_TICKET_STATUS.value:
            ticket_id = int(payload["ticketId"])
            old_ticket = self.get_ticket_by_id(ticket_id=ticket_id, auth_token=auth_token)
            new_ticket = self.update_ticket_status(
                auth_token=auth_token,
                ticket_id=ticket_id,
                req=UpdateTicketStatusRequest(status=payload["status"]),
            )
            return {
                "id": ticket_id,
                "oldStatus": old_ticket.status.value,
                "newStatus": new_ticket.status.value,
                "ticket": new_ticket.model_dump(mode="json", exclude_none=True),
            }

        if action_type == AiPendingActionType.SAVE_AI_REPLY.value:
            return {
                "id": 100 + int(payload["ticketId"]),
                "ticketId": int(payload["ticketId"]),
                "content": payload["content"],
                "replyType": "AI",
            }
        raise AssertionError(f"Unsupported pending action type: {action_type}")


@pytest.fixture(autouse=True)
def fake_java_ticket_client(monkeypatch: pytest.MonkeyPatch) -> None:
    reset_mock_tickets()
    FakeJavaTicketClient.seen_tokens.clear()
    FakeJavaTicketClient.pending_actions.clear()
    FakeJavaTicketClient.confirm_counts.clear()
    FakeJavaTicketClient.next_pending_action_id = 1
    monkeypatch.setattr(ticket_tools, "JavaTicketClient", FakeJavaTicketClient)
    monkeypatch.setattr(ticket_ai_service, "JavaTicketClient", FakeJavaTicketClient)
    monkeypatch.setattr(agent_tool_service, "JavaTicketClient", FakeJavaTicketClient)
    monkeypatch.setattr(ai_capability_base, "JavaTicketClient", FakeJavaTicketClient)
    agent_api.agent_tool_service.java_ticket_client = FakeJavaTicketClient()
