import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.clients.java_ticket_client import JavaApiError
from app.clients.llm_client import MOCK_REPLY_SUGGESTION
from app.core.exceptions import AppException, LLM_CALL_FAILED
from app.main import app
from app.schemas.pending_action import AiPendingActionType
from app.services.pending_intent_store import build_pending_intent_key
from app.services.ticket_ai_service import TicketAiService
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
    assert body["type"] == "NORMAL"


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
    assert response.json()["type"] == "PENDING_CONFIRMATION"
    assert response.json()["data"]["payload"] == {
        "title": "登录失败",
        "description": "用户输入正确密码后仍提示错误",
        "priority": "HIGH",
    }
    action = FakeJavaTicketClient.pending_actions[("7", "java-chat-1")]
    assert action["status"] == "PENDING"
    assert "auth_token" not in action["payload"]


def test_agent_chat_reply_suggestion_uses_ticket_ai_service() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    body = response.json()
    result = body["data"]
    assert body["type"] == "JSON_RESULT"
    assert body["answer"] == "回复建议生成完成"
    assert result["suggestion"] == MOCK_REPLY_SUGGESTION
    assert 0.0 <= result["confidence"] <= 1.0
    assert result["reason"]
    assert result["risk_flags"] == ["需要人工确认"]


def test_agent_chat_reply_suggestion_missing_ticket_id_does_not_save_pending_action() -> None:
    response = client.post("/agent/chat", json={"message": "这个工单怎么回复"})

    assert response.status_code == 200
    assert response.json()["answer"] == "请提供要生成回复建议的工单 ID。"
    assert response.json()["type"] == "MISSING_FIELDS"
    assert response.json()["data"] == {"missing_fields": ["ticket_id"], "collected": {}}
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


def test_agent_chat_unknown_returns_structured_unknown_intent() -> None:
    response = client.post("/agent/chat", json={"message": "随便聊点天气"})

    assert response.status_code == 200
    assert response.json()["type"] == "UNKNOWN_INTENT"
    assert response.json()["answer"] == "我还不能确定你的操作意图，请说明是查询、创建、修改状态，还是生成回复建议。"


def test_agent_chat_create_ticket_missing_fields_returns_structured_missing_fields() -> None:
    response = client.post("/agent/chat", json={"message": "创建工单"})

    assert response.status_code == 200
    assert response.json()["type"] == "MISSING_FIELDS"
    assert response.json()["data"] == {
        "missing_fields": ["title", "description", "priority"],
        "collected": {},
    }
    assert FakeJavaTicketClient.pending_actions == {}


def test_agent_chat_create_ticket_multi_turn_fills_missing_fields() -> None:
    first_response = client.post(
        "/agent/chat",
        json={
            "message": "帮我创建一个高优先级工单",
            "user_id": 7,
            "conversation_id": "multi-create",
            "auth_token": "java-token",
        },
    )

    assert first_response.status_code == 200
    assert first_response.json()["type"] == "MISSING_FIELDS"
    assert first_response.json()["data"] == {
        "missing_fields": ["title", "description"],
        "collected": {"priority": "HIGH"},
    }
    pending_key = build_pending_intent_key("7", "multi-create")
    pending_intent = agent_api.agent_tool_service.pending_intent_store.get(pending_key)
    assert pending_intent is not None
    assert pending_intent.collected == {"priority": "HIGH"}
    assert FakeJavaTicketClient.pending_actions == {}

    second_response = client.post(
        "/agent/chat",
        json={
            "message": "标题是数据库连接失败，描述是测试环境偶发无法连接",
            "user_id": 7,
            "conversation_id": "multi-create",
            "auth_token": "java-token",
        },
    )

    assert second_response.status_code == 200
    assert second_response.json()["type"] == "PENDING_CONFIRMATION"
    assert second_response.json()["data"] == {
        "actionType": AiPendingActionType.CREATE_TICKET.value,
        "payload": {
            "title": "数据库连接失败",
            "description": "测试环境偶发无法连接",
            "priority": "HIGH",
        },
    }
    assert agent_api.agent_tool_service.pending_intent_store.get(pending_key) is None
    action = FakeJavaTicketClient.pending_actions[("7", "multi-create")]
    assert action["actionType"] == AiPendingActionType.CREATE_TICKET.value
    assert action["payload"] == {
        "title": "数据库连接失败",
        "content": "测试环境偶发无法连接",
        "priority": "HIGH",
    }
    assert "token" not in action["payload"]

    confirm_response = client.post(
        "/agent/chat",
        json={
            "message": "确认",
            "user_id": 7,
            "conversation_id": "multi-create",
            "auth_token": "java-token",
        },
    )

    assert confirm_response.status_code == 200
    assert "已创建工单" in confirm_response.json()["answer"]
    assert FakeJavaTicketClient.confirm_counts[("7", "multi-create")] == 1


def test_agent_chat_update_status_missing_fields_returns_structured_missing_fields() -> None:
    response = client.post("/agent/chat", json={"message": "修改工单状态"})

    assert response.status_code == 200
    assert response.json()["type"] == "MISSING_FIELDS"
    assert response.json()["data"] == {
        "missing_fields": ["ticket_id", "target_status"],
        "collected": {},
    }
    assert FakeJavaTicketClient.pending_actions == {}


def test_agent_chat_update_status_multi_turn_fills_ticket_id_and_can_cancel() -> None:
    first_response = client.post(
        "/agent/chat",
        json={
            "message": "把工单改成处理中",
            "user_id": 7,
            "conversation_id": "multi-update",
            "auth_token": "java-token",
        },
    )

    assert first_response.status_code == 200
    assert first_response.json()["type"] == "MISSING_FIELDS"
    assert first_response.json()["data"] == {
        "missing_fields": ["ticket_id"],
        "collected": {"target_status": "PROCESSING"},
    }
    pending_key = build_pending_intent_key("7", "multi-update")
    pending_intent = agent_api.agent_tool_service.pending_intent_store.get(pending_key)
    assert pending_intent is not None
    assert pending_intent.collected == {"target_status": "PROCESSING"}
    assert FakeJavaTicketClient.pending_actions == {}

    second_response = client.post(
        "/agent/chat",
        json={
            "message": "3 号",
            "user_id": 7,
            "conversation_id": "multi-update",
            "auth_token": "java-token",
        },
    )

    assert second_response.status_code == 200
    assert second_response.json()["type"] == "PENDING_CONFIRMATION"
    assert second_response.json()["data"] == {
        "actionType": AiPendingActionType.UPDATE_TICKET_STATUS.value,
        "payload": {"ticket_id": 3, "target_status": "PROCESSING"},
    }
    assert agent_api.agent_tool_service.pending_intent_store.get(pending_key) is None
    action = FakeJavaTicketClient.pending_actions[("7", "multi-update")]
    assert action["actionType"] == AiPendingActionType.UPDATE_TICKET_STATUS.value
    assert action["payload"] == {"ticketId": 3, "status": "PROCESSING"}

    cancel_response = client.post(
        "/agent/chat",
        json={
            "message": "取消",
            "user_id": 7,
            "conversation_id": "multi-update",
            "auth_token": "java-token",
        },
    )

    assert cancel_response.status_code == 200
    assert cancel_response.json()["answer"] == "已取消修改工单状态，本次操作未执行。"
    assert FakeJavaTicketClient.pending_actions[("7", "multi-update")]["status"] == "CANCELLED"


def test_agent_chat_grounded_ticket_ai_missing_ticket_id_returns_structured_missing_fields() -> None:
    response = client.post("/agent/chat", json={"message": "总结这个工单"})

    assert response.status_code == 200
    assert response.json()["answer"] == "请提供要分析的工单 ID。"
    assert response.json()["type"] == "MISSING_FIELDS"
    assert response.json()["data"] == {"missing_fields": ["ticket_id"], "collected": {}}


def test_agent_chat_reply_suggestion_multi_turn_fills_ticket_id() -> None:
    first_response = client.post(
        "/agent/chat",
        json={
            "message": "这个工单怎么回复",
            "user_id": 7,
            "conversation_id": "multi-reply",
            "auth_token": "java-token",
        },
    )

    assert first_response.status_code == 200
    assert first_response.json()["type"] == "MISSING_FIELDS"
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("7", "multi-reply")
    ) is not None

    second_response = client.post(
        "/agent/chat",
        json={
            "message": "1 号",
            "user_id": 7,
            "conversation_id": "multi-reply",
            "auth_token": "java-token",
        },
    )

    assert second_response.status_code == 200
    assert second_response.json()["type"] == "JSON_RESULT"
    assert second_response.json()["answer"] == "回复建议生成完成"
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("7", "multi-reply")
    ) is None


def test_agent_chat_pending_intent_isolated_by_conversation_id() -> None:
    client.post(
        "/agent/chat",
        json={
            "message": "帮我创建一个高优先级工单",
            "user_id": 7,
            "conversation_id": "conv-a",
            "auth_token": "java-token",
        },
    )

    response_b = client.post(
        "/agent/chat",
        json={
            "message": "标题是数据库连接失败，描述是测试环境偶发无法连接",
            "user_id": 7,
            "conversation_id": "conv-b",
            "auth_token": "java-token",
        },
    )

    assert response_b.status_code == 200
    assert response_b.json()["type"] == "UNKNOWN_INTENT"
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("7", "conv-a")
    ) is not None
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("7", "conv-b")
    ) is None
    assert FakeJavaTicketClient.pending_actions == {}


def test_agent_chat_pending_intent_isolated_by_user_id() -> None:
    client.post(
        "/agent/chat",
        json={
            "message": "把工单改成处理中",
            "user_id": "1",
            "conversation_id": "shared-conv",
            "auth_token": "A_TOKEN",
        },
    )

    response_user_2 = client.post(
        "/agent/chat",
        json={
            "message": "3 号",
            "user_id": "2",
            "conversation_id": "shared-conv",
            "auth_token": "B_TOKEN",
        },
    )

    assert response_user_2.status_code == 200
    assert response_user_2.json()["type"] == "UNKNOWN_INTENT"
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("1", "shared-conv")
    ) is not None
    assert agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("2", "shared-conv")
    ) is None
    assert FakeJavaTicketClient.pending_actions == {}


def test_agent_chat_pending_intent_does_not_store_token() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": "帮我创建一个高优先级工单",
            "user_id": 7,
            "conversation_id": "safe-draft",
            "auth_token": "java-token",
        },
    )

    assert response.status_code == 200
    pending_intent = agent_api.agent_tool_service.pending_intent_store.get(
        build_pending_intent_key("7", "safe-draft")
    )
    assert pending_intent is not None
    payload = pending_intent.model_dump(mode="json")
    assert "auth_token" not in payload
    assert "Authorization" not in payload
    assert "JWT" not in payload
    assert "token" not in str(payload).lower()


def test_agent_chat_llm_failure_returns_structured_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingTicketAiService:
        def generate_reply_suggestion_by_ticket_id(self, *args, **kwargs):
            raise AppException(
                code=LLM_CALL_FAILED,
                message="LLM timeout",
                status_code=502,
            )

    monkeypatch.setattr(
        agent_api.agent_tool_service,
        "ticket_ai_service",
        FailingTicketAiService(),
    )

    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "ERROR"
    assert response.json()["answer"] == "AI 回复建议生成失败，请稍后重试或手动填写。"
    assert response.json()["risk_flags"] == ["LLM调用失败"]


def test_agent_chat_invalid_llm_json_returns_structured_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    service = TicketAiService(
        llm_client=StaticLLMClient("这不是 JSON"),  # type: ignore[arg-type]
        java_ticket_client=FakeJavaTicketClient(),  # type: ignore[arg-type]
    )
    monkeypatch.setattr(agent_api.agent_tool_service, "ticket_ai_service", service)

    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "ERROR"
    assert response.json()["answer"] == "AI 返回格式异常，请稍后重试。"
    assert response.json()["risk_flags"] == ["JSON解析失败"]


class StaticLLMClient:
    def __init__(self, content: str) -> None:
        self.content = content
        self.calls = 0

    def generate_text(self, prompt: str) -> str:
        self.calls += 1
        return self.content


class ForbiddenJavaTicketClient:
    def get_ticket_detail(self, auth_token: str | None, ticket_id: int):
        raise JavaApiError(403, "你没有权限执行该操作。")


class NotFoundJavaTicketClient:
    def get_ticket_detail(self, auth_token: str | None, ticket_id: int):
        raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")


@pytest.mark.parametrize(
    ("java_client", "expected_message"),
    [
        (ForbiddenJavaTicketClient(), "你没有权限执行该操作。"),
        (NotFoundJavaTicketClient(), "目标工单不存在，或你无权访问该工单。"),
    ],
)
def test_agent_chat_java_denial_does_not_call_llm(
    monkeypatch: pytest.MonkeyPatch,
    java_client,
    expected_message: str,
) -> None:
    llm_client = StaticLLMClient(
        '{"suggestion":"不应该生成","confidence":0.8,"reason":"不应该调用 LLM","risk_flags":[]}'
    )
    service = TicketAiService(
        llm_client=llm_client,  # type: ignore[arg-type]
        java_ticket_client=java_client,  # type: ignore[arg-type]
    )
    monkeypatch.setattr(agent_api.agent_tool_service, "ticket_ai_service", service)

    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == "FORBIDDEN"
    assert response.json()["answer"] == expected_message
    assert llm_client.calls == 0


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
    ("message", "expected_message", "expected_keys"),
    [
        ("总结 1 号工单", "工单摘要生成完成", {"summary", "key_points", "risk_flags"}),
        ("判断 1 号工单优先级是否合理", "优先级建议生成完成", {"suggested_priority", "confidence", "reason", "risk_flags"}),
        ("1 号工单属于什么分类", "分类建议生成完成", {"suggested_category", "confidence", "reason", "risk_flags"}),
        ("1 号工单有没有类似工单", "相似工单检索完成", {"similar_tickets", "risk_flags"}),
        ("1 号工单有 SLA 风险吗", "SLA 风险分析完成", {"sla_risk_level", "reason", "missing_fields", "risk_flags"}),
    ],
)
def test_agent_chat_new_ai_capabilities_return_structured_json(
    message: str,
    expected_message: str,
    expected_keys: set[str],
) -> None:
    response = client.post(
        "/agent/chat",
        json={"message": message, "auth_token": "java-token"},
    )

    assert response.status_code == 200
    body = response.json()
    result = body["data"]
    assert body["type"] == "JSON_RESULT"
    assert body["answer"] == expected_message
    assert set(result.keys()) == expected_keys
