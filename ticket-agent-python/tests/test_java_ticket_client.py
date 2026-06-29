import logging

import httpx
import pytest

from app.clients.java_ticket_client import JavaApiError, JavaTicketClient
from app.schemas.pending_action import (
    AiPendingActionConfirmResult,
    AiPendingActionDTO,
    AiPendingActionStatus,
    AiPendingActionType,
    CreateAiPendingActionRequest,
)
from app.schemas.ticket import (
    CreateTicketRequest,
    TicketDetailDTO,
    TicketDTO,
    TicketPriority,
    TicketReplyType,
    TicketStatus,
    UpdateTicketStatusRequest,
)


def test_search_tickets_parses_ticket_dto(monkeypatch: pytest.MonkeyPatch) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> dict:
        return {
            "records": [
                {
                    "id": 1,
                    "title": "登录失败",
                    "content": "用户无法登录",
                    "status": "OPEN",
                    "priority": "HIGH",
                    "userId": 7,
                }
            ],
            "total": 1,
            "page": 1,
            "size": 10,
        }

    monkeypatch.setattr(client, "_request", fake_request)

    tickets = client.search_tickets("java-token", {"status": "OPEN"})

    assert len(tickets) == 1
    assert isinstance(tickets[0], TicketDTO)
    assert tickets[0].id == 1
    assert tickets[0].description == "用户无法登录"
    assert tickets[0].status == TicketStatus.OPEN
    assert tickets[0].priority == TicketPriority.HIGH
    assert tickets[0].createdBy == 7


def test_search_tickets_parses_ticket_dto_when_optional_fields_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> list[dict]:
        return [
            {
                "id": 2,
                "title": "登录失败",
                "status": "OPEN",
            }
        ]

    monkeypatch.setattr(client, "_request", fake_request)

    tickets = client.search_tickets("java-token", {})

    assert len(tickets) == 1
    assert tickets[0].id == 2
    assert tickets[0].description is None
    assert tickets[0].priority is None
    assert tickets[0].createdBy is None


def test_list_tickets_delegates_to_search_tickets(monkeypatch: pytest.MonkeyPatch) -> None:
    client = JavaTicketClient(token="java-token")
    seen_query = {}

    def fake_request(*args, **kwargs) -> list[dict]:
        seen_query.update(kwargs["params"])
        return [{"id": 3, "title": "登录失败", "status": "OPEN"}]

    monkeypatch.setattr(client, "_request", fake_request)

    tickets = client.list_tickets("java-token", {"keyword": "登录"})

    assert seen_query["keyword"] == "登录"
    assert tickets[0].id == 3


def test_get_ticket_detail_parses_ticket_detail_dto(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> dict:
        return {
            "ticket": {
                "id": 3,
                "title": "文件上传异常",
                "content": "上传 PDF 时失败",
                "status": "PROCESSING",
                "priority": "MEDIUM",
                "userId": 9,
            },
            "replies": [
                {
                    "id": 8,
                    "ticketId": 3,
                    "userId": 2,
                    "content": "已收到问题，正在排查。",
                    "replyType": "STAFF",
                }
            ],
        }

    monkeypatch.setattr(client, "_request", fake_request)

    detail = client.get_ticket_detail("java-token", 3)

    assert isinstance(detail, TicketDetailDTO)
    assert detail.id == 3
    assert detail.description == "上传 PDF 时失败"
    assert detail.status == TicketStatus.PROCESSING
    assert detail.replies[0].type == TicketReplyType.STAFF
    assert detail.replies[0].content == "已收到问题，正在排查。"


def test_create_ticket_returns_ticket_dto(monkeypatch: pytest.MonkeyPatch) -> None:
    client = JavaTicketClient(token="java-token")
    seen_body = {}

    def fake_request(*args, **kwargs) -> dict:
        seen_body.update(kwargs["json"])
        return {
            "id": 10,
            "title": "支付失败",
            "content": "支付成功但订单未更新",
            "status": "OPEN",
            "priority": "URGENT",
            "userId": 1,
        }

    monkeypatch.setattr(client, "_request", fake_request)

    ticket = client.create_ticket(
        "java-token",
        CreateTicketRequest(
            title="支付失败",
            description="支付成功但订单未更新",
            priority=TicketPriority.URGENT,
        ),
    )

    assert seen_body == {
        "title": "支付失败",
        "content": "支付成功但订单未更新",
        "priority": "URGENT",
    }
    assert ticket.id == 10
    assert ticket.priority == TicketPriority.URGENT


def test_update_ticket_status_returns_ticket_dto(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> dict:
        return {
            "id": 1,
            "title": "登录失败",
            "content": "用户无法登录",
            "status": "PROCESSING",
            "priority": "HIGH",
            "userId": 7,
        }

    monkeypatch.setattr(client, "_request", fake_request)

    ticket = client.update_ticket_status(
        "java-token",
        1,
        UpdateTicketStatusRequest(status=TicketStatus.PROCESSING),
    )

    assert ticket.id == 1
    assert ticket.status == TicketStatus.PROCESSING


def test_create_pending_action_returns_dto(monkeypatch: pytest.MonkeyPatch) -> None:
    client = JavaTicketClient(token="java-token")
    seen_body = {}

    def fake_request(*args, **kwargs) -> dict:
        seen_body.update(kwargs["json"])
        return {
            "id": 1,
            "userId": 7,
            "conversationId": "chat-1",
            "actionType": "CREATE_TICKET",
            "payload": {
                "title": "登录失败",
                "content": "用户无法登录",
                "priority": "HIGH",
            },
            "status": "PENDING",
        }

    monkeypatch.setattr(client, "_request", fake_request)

    action = client.create_pending_action(
        "java-token",
        CreateAiPendingActionRequest(
            conversationId="chat-1",
            actionType=AiPendingActionType.CREATE_TICKET,
            payload={
                "title": "登录失败",
                "content": "用户无法登录",
                "priority": "HIGH",
            },
        ),
    )

    assert isinstance(action, AiPendingActionDTO)
    assert seen_body["conversationId"] == "chat-1"
    assert seen_body["actionType"] == "CREATE_TICKET"
    assert "auth_token" not in seen_body["payload"]
    assert action.status == AiPendingActionStatus.PENDING


def test_get_current_pending_action_returns_none(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = JavaTicketClient(token="java-token")

    monkeypatch.setattr(client, "_request", lambda *args, **kwargs: None)

    assert client.get_current_pending_action("java-token", "chat-1") is None


def test_confirm_pending_action_returns_result(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> dict:
        return {
            "pendingAction": {
                "id": 1,
                "userId": 7,
                "conversationId": "chat-1",
                "actionType": "UPDATE_TICKET_STATUS",
                "payload": {"ticketId": 1, "status": "PROCESSING"},
                "status": "CONFIRMED",
            },
            "result": {
                "id": 1,
                "oldStatus": "OPEN",
                "newStatus": "PROCESSING",
            },
        }

    monkeypatch.setattr(client, "_request", fake_request)

    result = client.confirm_pending_action("java-token", "chat-1")

    assert isinstance(result, AiPendingActionConfirmResult)
    assert result.pendingAction.actionType == AiPendingActionType.UPDATE_TICKET_STATUS
    assert result.pendingAction.status == AiPendingActionStatus.CONFIRMED
    assert result.result["newStatus"] == "PROCESSING"


def test_cancel_pending_action_returns_dto(monkeypatch: pytest.MonkeyPatch) -> None:
    client = JavaTicketClient(token="java-token")

    def fake_request(*args, **kwargs) -> dict:
        return {
            "id": 1,
            "userId": 7,
            "conversationId": "chat-1",
            "actionType": "CREATE_TICKET",
            "payload": {"title": "登录失败", "content": "用户无法登录"},
            "status": "CANCELLED",
        }

    monkeypatch.setattr(client, "_request", fake_request)

    action = client.cancel_pending_action("java-token", "chat-1")

    assert action.status == AiPendingActionStatus.CANCELLED


@pytest.mark.parametrize(
    ("code", "expected_message"),
    [
        (401, "登录状态已失效，请重新登录。"),
        (403, "你没有权限执行该操作。"),
        (404, "目标工单不存在，或你无权访问该工单。"),
        (500, "服务暂时异常，请稍后重试。"),
    ],
)
def test_unwrap_result_maps_java_business_errors(
    code: int,
    expected_message: str,
) -> None:
    response = httpx.Response(
        200,
        json={
            "code": code,
            "message": "error",
            "data": None,
        },
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == code
    assert exc_info.value.message == expected_message


def test_unwrap_result_uses_java_400_message() -> None:
    response = httpx.Response(
        200,
        json={
            "code": 400,
            "message": "工单状态不合法",
            "data": None,
        },
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == 400
    assert exc_info.value.message == "工单状态不合法"


@pytest.mark.parametrize(
    ("status_code", "expected_message"),
    [
        (401, "登录状态已失效，请重新登录。"),
        (403, "你没有权限执行该操作。"),
        (404, "目标工单不存在，或你无权访问该工单。"),
        (500, "服务暂时异常，请稍后重试。"),
    ],
)
def test_http_errors_map_to_java_api_error_messages(
    status_code: int,
    expected_message: str,
) -> None:
    response = httpx.Response(
        status_code,
        json={
            "message": "raw java message",
        },
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == status_code
    assert exc_info.value.message == expected_message


def test_http_error_maps_to_java_api_error() -> None:
    response = httpx.Response(
        403,
        json={
            "message": "forbidden",
        },
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == 403
    assert exc_info.value.message == "你没有权限执行该操作。"


def test_unwrap_result_non_json_http_error_is_readable() -> None:
    response = httpx.Response(
        500,
        content=b"<html>Internal Server Error</html>",
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == 500
    assert exc_info.value.message == "服务暂时异常，请稍后重试。"
    assert exc_info.value.data == {
        "raw_response": "<html>Internal Server Error</html>"
    }


def test_unwrap_result_non_json_success_is_readable() -> None:
    response = httpx.Response(
        200,
        content=b"OK",
    )

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._unwrap_result(response)

    assert exc_info.value.status_code == 200
    assert exc_info.value.message == "Java API 返回非 JSON 响应，请检查后端接口。"


def test_request_connection_failure_is_readable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingClient:
        def __init__(self, timeout: float) -> None:
            self.timeout = timeout

        def __enter__(self) -> "FailingClient":
            return self

        def __exit__(self, exc_type, exc, traceback) -> None:
            return None

        def request(self, *args, **kwargs) -> httpx.Response:
            request = httpx.Request("GET", "http://127.0.0.1:8080/tickets")
            raise httpx.ConnectError("connection refused", request=request)

    monkeypatch.setattr(httpx, "Client", FailingClient)

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient(token="java-token")._request("GET", "/tickets")

    assert exc_info.value.status_code == 502
    assert exc_info.value.message == "无法连接 Java 后端服务，请确认 Java 后端已启动。"


def test_request_timeout_is_readable(monkeypatch: pytest.MonkeyPatch) -> None:
    class TimeoutClient:
        def __init__(self, timeout: float) -> None:
            self.timeout = timeout

        def __enter__(self) -> "TimeoutClient":
            return self

        def __exit__(self, exc_type, exc, traceback) -> None:
            return None

        def request(self, *args, **kwargs) -> httpx.Response:
            request = httpx.Request("GET", "http://127.0.0.1:8080/tickets")
            raise httpx.ReadTimeout("timeout", request=request)

    monkeypatch.setattr(httpx, "Client", TimeoutClient)

    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient(token="java-token")._request("GET", "/tickets")

    assert exc_info.value.status_code == 504
    assert exc_info.value.message == "Java 后端响应超时，请稍后重试。"


def test_headers_require_authorization() -> None:
    with pytest.raises(JavaApiError) as exc_info:
        JavaTicketClient()._headers()

    assert exc_info.value.status_code == 401
    assert exc_info.value.message == "登录状态已失效，请重新登录。"


def test_request_logs_do_not_include_full_token(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    class SuccessClient:
        def __init__(self, timeout: float) -> None:
            self.timeout = timeout

        def __enter__(self) -> "SuccessClient":
            return self

        def __exit__(self, exc_type, exc, traceback) -> None:
            return None

        def request(self, *args, **kwargs) -> httpx.Response:
            return httpx.Response(
                200,
                json={"code": 200, "message": "ok", "data": []},
            )

    monkeypatch.setattr(httpx, "Client", SuccessClient)
    caplog.set_level(logging.INFO, logger="app.clients.java_ticket_client")

    JavaTicketClient(token="java-token")._request(
        "GET",
        "/tickets",
        auth_token="Bearer full-secret-token",
    )

    assert "full-secret-token" not in caplog.text
    assert "Bearer full-secret-token" not in caplog.text
