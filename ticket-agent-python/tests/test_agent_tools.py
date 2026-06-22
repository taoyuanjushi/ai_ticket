import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.main import app
from app.tools.tool_registry import get_all_tools

client = TestClient(app)


@pytest.fixture(autouse=True)
def use_mock_llm(monkeypatch: pytest.MonkeyPatch) -> None:
    agent_api.agent_state_service.clear_pending_action()
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_api_key", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_base_url", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_model", "")


def test_get_all_tools_returns_tools() -> None:
    tools = get_all_tools()

    assert tools


def test_get_all_tools_contains_capability_tool() -> None:
    tools = get_all_tools()

    assert any(tool.name == "get_agent_capabilities" for tool in tools)


def test_agent_tools_returns_200() -> None:
    response = client.get("/agent/tools")

    assert response.status_code == 200


def test_agent_tools_returns_tool_name() -> None:
    response = client.get("/agent/tools")

    tool_names = [tool["name"] for tool in response.json()["tools"]]
    assert "get_agent_capabilities" in tool_names


def test_agent_chat_capability_question_calls_tool() -> None:
    response = client.post("/agent/chat", json={"message": "你能做什么？"})

    assert response.status_code == 200
    assert "LangChain Tool 基础阶段" in response.json()["answer"]
    assert "查询工单" in response.json()["answer"]


def test_agent_chat_function_question_calls_tool() -> None:
    response = client.post("/agent/chat", json={"message": "你有哪些功能？"})

    assert response.status_code == 200
    assert "修改工单状态" in response.json()["answer"]


def test_agent_chat_normal_message_falls_back_to_mock_llm() -> None:
    response = client.post("/agent/chat", json={"message": "你好"})

    assert response.status_code == 200
    assert response.json()["answer"]


def test_agent_chat_tool_failure_returns_friendly_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingTool:
        def invoke(self, tool_input: dict) -> dict:
            raise RuntimeError("tool failed")

    monkeypatch.setattr(agent_api.agent_tool_service, "capability_tool", FailingTool())

    response = client.post("/agent/chat", json={"message": "你能做什么？"})

    assert response.status_code == 500
    assert response.json() == {
        "success": False,
        "code": "TOOL_CALL_FAILED",
        "message": "工具调用失败，请稍后重试",
        "detail": None,
    }
