import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.main import app
from app.schemas.pending_action import AiPendingActionType
from app.services.agent_tool_service import UNKNOWN_INTENT_ANSWER
from app.tools.mock_ticket_data import reset_mock_tickets
from app.tools.ticket_tools import search_tickets, update_ticket_status
from app.tools.tool_registry import get_all_tools

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_tickets_and_use_mock_llm(monkeypatch: pytest.MonkeyPatch):
    reset_mock_tickets()
    agent_api.agent_state_service.clear_pending_action()
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_api_key", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_base_url", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_model", "")
    yield
    reset_mock_tickets()
    agent_api.agent_state_service.clear_pending_action()


def test_get_all_tools_contains_update_ticket_status() -> None:
    tools = get_all_tools()

    assert any(tool.name == "update_ticket_status" for tool in tools)


def test_agent_tools_returns_update_ticket_status() -> None:
    response = client.get("/agent/tools")

    tool_names = [tool["name"] for tool in response.json()["tools"]]
    assert "update_ticket_status" in tool_names


def test_update_ticket_status_open_to_processing() -> None:
    result = update_ticket_status.invoke({"ticket_id": 1, "status": "PROCESSING"})

    assert result["success"] is True
    assert result["id"] == 1
    assert result["old_status"] == "OPEN"
    assert result["new_status"] == "PROCESSING"


def test_update_ticket_status_processing_to_closed() -> None:
    result = update_ticket_status.invoke({"ticket_id": 2, "status": "CLOSED"})

    assert result["success"] is True
    assert result["old_status"] == "PROCESSING"
    assert result["new_status"] == "CLOSED"


def test_update_ticket_status_open_to_closed() -> None:
    result = update_ticket_status.invoke({"ticket_id": 1, "status": "CLOSED"})

    assert result["success"] is True
    assert result["old_status"] == "OPEN"
    assert result["new_status"] == "CLOSED"


def test_update_ticket_status_ticket_not_found_returns_error() -> None:
    result = update_ticket_status.invoke({"ticket_id": 999, "status": "PROCESSING"})

    assert result["success"] is False
    assert result["code"] == "JAVA_API_ERROR"
    assert "目标工单不存在，或你无权访问该工单。" in result["message"]


def test_update_ticket_status_processing_to_open_is_invalid_transition() -> None:
    result = update_ticket_status.invoke({"ticket_id": 2, "status": "OPEN"})

    assert result["success"] is False
    assert result["code"] == "JAVA_API_ERROR"
    assert "工单状态流转不合法" in result["message"]


def test_update_ticket_status_closed_cannot_change_to_open() -> None:
    result = update_ticket_status.invoke({"ticket_id": 5, "status": "OPEN"})

    assert result["success"] is False
    assert "工单状态流转不合法" in result["message"]


def test_update_ticket_status_closed_cannot_change_to_processing() -> None:
    result = update_ticket_status.invoke({"ticket_id": 5, "status": "PROCESSING"})

    assert result["success"] is False
    assert "工单状态流转不合法" in result["message"]


def test_update_ticket_status_same_status_returns_error() -> None:
    result = update_ticket_status.invoke({"ticket_id": 5, "status": "CLOSED"})

    assert result["success"] is False
    assert "无需重复修改" in result["message"]


def test_agent_chat_update_open_ticket_to_processing() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 1 号工单改成处理中", "auth_token": "A_TOKEN"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["type"] == "PENDING_CONFIRMATION"
    assert body["data"] == {
        "actionType": AiPendingActionType.UPDATE_TICKET_STATUS.value,
        "payload": {"ticket_id": 1, "target_status": "PROCESSING"},
    }

    confirm_response = client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})
    answer = confirm_response.json()["answer"]
    assert "已将 1 号工单状态从 OPEN 修改为 PROCESSING" in answer


def test_agent_chat_update_processing_ticket_to_closed_from_completed_text() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "将 2 号工单状态改为已完成", "auth_token": "A_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "PENDING_CONFIRMATION"
    assert response.json()["data"]["payload"] == {"ticket_id": 2, "target_status": "CLOSED"}

    confirm_response = client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})
    assert "已将 2 号工单状态从 PROCESSING 修改为 CLOSED" in confirm_response.json()["answer"]


def test_agent_chat_close_open_ticket_succeeds() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "关闭 1 号工单", "auth_token": "A_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "PENDING_CONFIRMATION"
    assert response.json()["data"]["payload"] == {"ticket_id": 1, "target_status": "CLOSED"}

    confirm_response = client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})
    assert "已将 1 号工单状态从 OPEN 修改为 CLOSED" in confirm_response.json()["answer"]


def test_agent_chat_ticket_not_found_returns_friendly_message() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 999 号工单改成处理中", "auth_token": "A_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "PENDING_CONFIRMATION"
    assert response.json()["data"]["payload"] == {
        "ticket_id": 999,
        "target_status": "PROCESSING",
    }

    confirm_response = client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})
    assert "目标工单不存在，或你无权访问该工单。" in confirm_response.json()["answer"]


def test_agent_chat_missing_ticket_id_asks_for_id() -> None:
    response = client.post("/agent/chat", json={"message": "把工单改成处理中"})

    assert response.status_code == 200
    assert "修改工单状态还缺少必要信息：工单 ID" in response.json()["answer"]


def test_agent_chat_missing_status_asks_for_target_status() -> None:
    response = client.post("/agent/chat", json={"message": "修改 3 号工单状态"})

    assert response.status_code == 200
    assert "修改工单状态还缺少必要信息：目标状态" in response.json()["answer"]


def test_agent_chat_missing_ticket_id_and_status_asks_for_both() -> None:
    response = client.post("/agent/chat", json={"message": "修改工单状态"})

    assert response.status_code == 200
    assert response.json()["answer"] == "请说明要修改的工单 ID 和目标状态。"


def test_agent_chat_invalid_status_returns_friendly_message() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 3 号工单改成异常状态"},
    )

    assert response.status_code == 200
    assert "目标状态不合法" in response.json()["answer"]


def test_updated_ticket_can_be_found_by_search_tickets_status() -> None:
    update_ticket_status.invoke({"ticket_id": 1, "status": "PROCESSING"})

    result = search_tickets.invoke({"status": "PROCESSING"})

    assert result["total"] == 3
    assert 1 in {ticket["id"] for ticket in result["items"]}


def test_agent_chat_search_processing_tickets_still_uses_search_tool() -> None:
    response = client.post("/agent/chat", json={"message": "查一下处理中工单"})

    assert response.status_code == 200
    assert "共找到 2 个工单" in response.json()["answer"]


def test_agent_chat_normal_message_still_falls_back_to_mock_llm() -> None:
    response = client.post("/agent/chat", json={"message": "你好"})

    assert response.status_code == 200
    assert response.json()["answer"] == UNKNOWN_INTENT_ANSWER
