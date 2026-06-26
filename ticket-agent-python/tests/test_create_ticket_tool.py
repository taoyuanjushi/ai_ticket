import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.main import app
from app.schemas.pending_action import AiPendingActionType
from app.tools.mock_ticket_data import MOCK_TICKETS, reset_mock_tickets
from app.tools.ticket_tools import create_ticket, search_tickets
from app.tools.tool_registry import get_all_tools
from app.services.agent_tool_service import UNKNOWN_INTENT_ANSWER

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


def test_get_all_tools_contains_create_ticket() -> None:
    tools = get_all_tools()

    assert any(tool.name == "create_ticket" for tool in tools)


def test_agent_tools_returns_create_ticket() -> None:
    response = client.get("/agent/tools")

    tool_names = [tool["name"] for tool in response.json()["tools"]]
    assert "create_ticket" in tool_names


def test_create_ticket_with_complete_params_creates_ticket() -> None:
    result = create_ticket.invoke(
        {
            "title": "登录失败",
            "description": "用户输入正确密码后仍提示错误",
            "priority": "HIGH",
        }
    )

    assert result["success"] is True
    assert result["message"] == "工单创建成功"
    assert result["ticket"]["title"] == "登录失败"


def test_create_ticket_defaults_status_to_open() -> None:
    result = create_ticket.invoke(
        {
            "title": "登录失败",
            "description": "用户输入正确密码后仍提示错误",
            "priority": "HIGH",
        }
    )

    assert result["ticket"]["status"] == "OPEN"


def test_create_ticket_uses_max_id_plus_one() -> None:
    result = create_ticket.invoke(
        {
            "title": "支付回调失败",
            "description": "支付成功但订单未更新",
            "priority": "URGENT",
        }
    )

    assert result["ticket"]["id"] == 6


def test_created_ticket_can_be_found_by_search_tickets() -> None:
    create_ticket.invoke(
        {
            "title": "支付回调失败",
            "description": "支付成功但订单未更新",
            "priority": "URGENT",
        }
    )

    result = search_tickets.invoke({"keyword": "支付回调"})

    assert result["total"] == 1
    assert result["items"][0]["title"] == "支付回调失败"


def test_agent_chat_complete_create_request_creates_ticket() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": "A_TOKEN",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["type"] == "PENDING_CONFIRMATION"
    assert body["answer"] == "请确认是否创建该工单。"
    assert body["data"] == {
        "actionType": AiPendingActionType.CREATE_TICKET.value,
        "payload": {
            "title": "登录失败",
            "description": "用户输入正确密码后仍提示错误",
            "priority": "HIGH",
        },
    }
    assert len(MOCK_TICKETS) == 5

    confirm_response = client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})
    answer = confirm_response.json()["answer"]
    assert "已创建工单：ID 6" in answer
    assert "标题：登录失败" in answer
    assert "优先级 HIGH" in answer
    assert "状态 OPEN" in answer


def test_agent_chat_created_ticket_can_be_searched() -> None:
    client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": "A_TOKEN",
        },
    )
    client.post("/agent/chat", json={"message": "确认", "auth_token": "A_TOKEN"})

    result = search_tickets.invoke({"keyword": "仍提示错误"})

    assert result["total"] == 1
    assert result["items"][0]["id"] == 6


def test_agent_chat_missing_title_asks_for_required_field() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我创建一个工单，描述是用户无法登录，优先级高"},
    )

    assert response.status_code == 200
    assert "创建工单还缺少必要信息：标题" in response.json()["answer"]


def test_agent_chat_missing_description_asks_for_required_field() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我创建一个工单，标题是登录失败，优先级高"},
    )

    assert response.status_code == 200
    assert "创建工单还缺少必要信息：描述" in response.json()["answer"]


def test_agent_chat_missing_priority_asks_for_required_field() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我创建一个工单，标题是登录失败，描述是用户无法登录"},
    )

    assert response.status_code == 200
    answer = response.json()["answer"]
    assert "创建工单还缺少必要信息：优先级" in answer
    assert "LOW、MEDIUM、HIGH" in answer


def test_agent_chat_incomplete_free_text_does_not_invent_fields() -> None:
    before_count = len(MOCK_TICKETS)

    response = client.post(
        "/agent/chat",
        json={"message": "帮我创建一个登录问题工单"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "请补充工单标题、描述和优先级。"
    assert len(MOCK_TICKETS) == before_count


def test_agent_chat_high_priority_text_maps_to_high() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个高优先级工单，标题是高优先级示例，"
                "描述是用于测试高优先级"
            ),
            "auth_token": "A_TOKEN",
        },
    )

    assert response.status_code == 200
    assert response.json()["data"]["payload"]["priority"] == "HIGH"


def test_agent_chat_urgent_text_maps_to_urgent() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "提交一个紧急工单，标题是支付回调失败，"
                "描述是支付成功但订单未更新"
            ),
            "auth_token": "A_TOKEN",
        },
    )

    assert response.status_code == 200
    assert response.json()["data"]["payload"]["priority"] == "URGENT"


def test_agent_chat_invalid_priority_returns_friendly_message() -> None:
    before_count = len(MOCK_TICKETS)

    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户无法登录，优先级最高"
            )
        },
    )

    assert response.status_code == 200
    assert "优先级不合法" in response.json()["answer"]
    assert len(MOCK_TICKETS) == before_count


def test_agent_chat_normal_message_still_falls_back_to_mock_llm() -> None:
    response = client.post("/agent/chat", json={"message": "你好"})

    assert response.status_code == 200
    assert response.json()["answer"] == UNKNOWN_INTENT_ANSWER
