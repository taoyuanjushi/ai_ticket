import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.main import app
from app.services.agent_tool_service import UNKNOWN_INTENT_ANSWER
from app.tools.ticket_tools import search_tickets
from app.tools.tool_registry import get_all_tools
from tests.conftest import FakeJavaTicketClient

client = TestClient(app)


@pytest.fixture(autouse=True)
def use_mock_llm(monkeypatch: pytest.MonkeyPatch) -> None:
    agent_api.agent_state_service.clear_pending_action()
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_api_key", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_base_url", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_model", "")


def test_get_all_tools_contains_search_tickets() -> None:
    tools = get_all_tools()

    assert any(tool.name == "search_tickets" for tool in tools)


def test_agent_tools_returns_search_tickets() -> None:
    response = client.get("/agent/tools")

    tool_names = [tool["name"] for tool in response.json()["tools"]]
    assert "search_tickets" in tool_names


def test_search_tickets_without_params_returns_all_tickets() -> None:
    result = search_tickets.invoke({})

    assert result["total"] == 5
    assert len(result["items"]) == 5


def test_search_tickets_filters_by_status() -> None:
    result = search_tickets.invoke({"status": "OPEN"})

    assert result["total"] == 2
    assert all(ticket["status"] == "OPEN" for ticket in result["items"])


def test_search_tickets_filters_by_priority() -> None:
    result = search_tickets.invoke({"priority": "HIGH"})

    assert result["total"] == 2
    assert all(ticket["priority"] == "HIGH" for ticket in result["items"])


def test_search_tickets_filters_by_status_and_priority() -> None:
    result = search_tickets.invoke({"status": "OPEN", "priority": "HIGH"})

    assert result["total"] == 1
    assert result["items"][0]["id"] == 1
    assert result["items"][0]["status"] == "OPEN"
    assert result["items"][0]["priority"] == "HIGH"


def test_search_tickets_filters_by_keyword() -> None:
    result = search_tickets.invoke({"keyword": "登录"})

    assert result["total"] == 2
    assert {ticket["id"] for ticket in result["items"]} == {1, 3}


def test_search_tickets_keyword_not_found_returns_empty_result() -> None:
    result = search_tickets.invoke({"keyword": "不存在"})

    assert result["total"] == 0
    assert result["items"] == []


def test_agent_chat_search_all_tickets_returns_ticket_list() -> None:
    response = client.post("/agent/chat", json={"message": "查一下所有工单"})

    assert response.status_code == 200
    assert "共找到 5 个工单" in response.json()["answer"]
    assert "ID 1：登录失败" in response.json()["answer"]
    assert "ID 5：历史工单归档" in response.json()["answer"]


def test_agent_chat_search_passes_auth_token_to_java_client() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "查一下所有工单", "auth_token": "test-token"},
    )

    assert response.status_code == 200
    assert "test-token" in FakeJavaTicketClient.seen_tokens


def test_agent_chat_search_high_open_tickets() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我查一下高优先级未处理工单"},
    )

    assert response.status_code == 200
    answer = response.json()["answer"]
    assert "共找到 1 个工单" in answer
    assert "ID 1：登录失败" in answer
    assert "优先级 HIGH" in answer
    assert "状态 OPEN" in answer


def test_agent_chat_search_processing_tickets_does_not_extract_medium_priority() -> None:
    response = client.post("/agent/chat", json={"message": "查一下处理中工单"})

    assert response.status_code == 200
    answer = response.json()["answer"]
    assert "共找到 2 个工单" in answer
    assert "ID 2：文件上传异常" in answer
    assert "ID 3：登录接口超时" in answer


def test_agent_chat_search_keyword_tickets() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "查询包含登录的工单"},
    )

    assert response.status_code == 200
    answer = response.json()["answer"]
    assert "共找到 2 个工单" in answer
    assert "ID 1：登录失败" in answer
    assert "ID 3：登录接口超时" in answer


def test_agent_chat_search_missing_keyword_returns_friendly_empty_answer() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "查询包含不存在的工单"},
    )

    assert response.status_code == 200
    assert "没有找到符合条件的工单" in response.json()["answer"]
    assert "keyword=不存在" in response.json()["answer"]


def test_agent_chat_normal_message_still_falls_back_to_mock_llm() -> None:
    response = client.post("/agent/chat", json={"message": "你好"})

    assert response.status_code == 200
    assert response.json()["answer"] == UNKNOWN_INTENT_ANSWER
