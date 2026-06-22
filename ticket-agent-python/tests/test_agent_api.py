import json

import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.clients.llm_client import MOCK_REPLY_SUGGESTION
from app.main import app
from app.schemas.pending_action import AiPendingActionType
from tests.conftest import FakeJavaTicketClient


@pytest.fixture(autouse=True)
def use_mock_llm(monkeypatch: pytest.MonkeyPatch) -> None:
    agent_api.agent_state_service.clear_pending_action()
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_api_key", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_base_url", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_model", "")


client = TestClient(app)


def test_health_returns_200() -> None:
    response = client.get("/health")

    assert response.status_code == 200


def test_health_returns_ok() -> None:
    response = client.get("/health")

    assert response.json()["status"] == "ok"


def test_agent_chat_returns_200() -> None:
    response = client.post("/agent/chat", json={"message": "你好，你能做什么？"})

    assert response.status_code == 200


def test_agent_chat_returns_answer() -> None:
    response = client.post("/agent/chat", json={"message": "你好，你能做什么？"})

    body = response.json()
    assert body["answer"]


def test_agent_chat_empty_message_returns_validation_error() -> None:
    response = client.post("/agent/chat", json={"message": "   "})

    assert response.status_code == 422
    assert response.json()["code"] == "INVALID_PARAMS"


def test_agent_chat_accepts_numeric_user_id_from_java() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "user_id": 7,
            "conversation_id": "java-chat-1",
            "auth_token": "Bearer java-token",
        },
    )

    assert response.status_code == 200
    assert "请确认：是否创建以下工单" in response.json()["answer"]
    action = FakeJavaTicketClient.pending_actions[("7", "java-chat-1")]
    assert action["status"] == "PENDING"
    assert "auth_token" not in action["payload"]


def test_agent_chat_reply_suggestion_uses_ticket_ai_service() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    answer = response.json()["answer"]
    result = json.loads(answer)
    assert result["suggestion"] == MOCK_REPLY_SUGGESTION
    assert 0.0 <= result["confidence"] <= 1.0
    assert result["reason"]
    assert result["risk_flags"] == ["需要人工确认"]


def test_agent_chat_reply_suggestion_missing_ticket_id_does_not_save_pending_action() -> None:
    response = client.post("/agent/chat", json={"message": "这个工单怎么回复"})

    assert response.status_code == 200
    assert response.json()["answer"] == "请提供要生成回复建议的工单 ID。"
    assert FakeJavaTicketClient.pending_actions == {}


def test_agent_chat_java_pending_action_connection_failure_is_readable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.clients.java_ticket_client import JavaApiError

    class FailingPendingActionClient:
        def create_pending_action(self, *args, **kwargs):
            raise JavaApiError(
                502,
                "无法连接 Java 后端服务，请确认 Java 后端已启动。",
            )

    monkeypatch.setattr(
        agent_api.agent_tool_service,
        "java_ticket_client",
        FailingPendingActionClient(),
    )

    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": "java-token",
        },
    )

    assert response.status_code == 502
    assert response.json()["message"] == "无法连接 Java 后端服务，请确认 Java 后端已启动。"


def test_agent_chat_save_reply_suggestion_creates_pending_action() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": "保存 1 号工单的 AI 回复建议，内容是请用户补充错误截图。",
            "auth_token": "java-token",
        },
    )

    assert response.status_code == 200
    assert "请确认：是否保存 1 号工单的 AI 回复建议" in response.json()["answer"]
    action = FakeJavaTicketClient.pending_actions[("7", "default")]
    assert action["status"] == "PENDING"
    assert action["actionType"] == AiPendingActionType.SAVE_AI_REPLY.value
    assert action["payload"]["content"] == "请用户补充错误截图"


@pytest.mark.parametrize(
    ("message", "expected_keys"),
    [
        ("总结 1 号工单", {"summary", "key_points", "risk_flags"}),
        ("判断 1 号工单优先级是否合理", {"suggested_priority", "confidence", "reason", "risk_flags"}),
        ("1 号工单属于什么分类", {"suggested_category", "confidence", "reason", "risk_flags"}),
        ("1 号工单有没有类似工单", {"similar_tickets", "risk_flags"}),
        ("1 号工单有 SLA 风险吗", {"sla_risk_level", "reason", "missing_fields", "risk_flags"}),
    ],
)
def test_agent_chat_new_ai_capabilities_return_structured_json(
    message: str,
    expected_keys: set[str],
) -> None:
    response = client.post(
        "/agent/chat",
        json={"message": message, "auth_token": "java-token"},
    )

    assert response.status_code == 200
    result = json.loads(response.json()["answer"])
    assert set(result.keys()) == expected_keys
