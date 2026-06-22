import pytest
from fastapi.testclient import TestClient

from app.api import agent_api
from app.main import app
from app.services.agent_tool_service import UNKNOWN_INTENT_ANSWER
from app.tools.mock_ticket_data import MOCK_TICKETS, reset_mock_tickets
from app.tools.ticket_tools import find_ticket_by_id, search_tickets
from tests.conftest import FakeJavaTicketClient

client = TestClient(app)
AUTH_TOKEN = "A_TOKEN"
NEW_AUTH_TOKEN = "A_TOKEN_NEW"
CREATE_TICKET_MESSAGE = (
    "帮我创建一个工单，标题是登录失败，"
    "描述是用户输入正确密码后仍提示错误，优先级高"
)


def has_java_pending_action(
    auth_token: str | None = AUTH_TOKEN,
    conversation_id: str = "default",
) -> bool:
    action = get_java_pending_action(auth_token, conversation_id)
    return bool(action and action["status"] == "PENDING")


def get_java_pending_action(
    auth_token: str | None = AUTH_TOKEN,
    conversation_id: str = "default",
) -> dict | None:
    token = auth_token[7:].strip() if auth_token and auth_token.startswith("Bearer ") else auth_token
    user_id = FakeJavaTicketClient.TOKEN_USER_IDS.get(token or AUTH_TOKEN)
    return FakeJavaTicketClient.pending_actions.get((str(user_id), conversation_id))


@pytest.fixture(autouse=True)
def reset_tickets_state_and_use_mock_llm(monkeypatch: pytest.MonkeyPatch):
    reset_mock_tickets()
    agent_api.agent_state_service.clear_pending_action()
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_api_key", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_base_url", "")
    monkeypatch.setattr(agent_api.llm_service.settings, "llm_model", "")
    yield
    reset_mock_tickets()
    agent_api.agent_state_service.clear_pending_action()


def test_search_tickets_does_not_need_confirmation() -> None:
    response = client.post("/agent/chat", json={"message": "查一下所有工单"})

    assert response.status_code == 200
    assert "共找到 5 个工单" in response.json()["answer"]
    assert has_java_pending_action() is False


def test_create_ticket_request_creates_pending_action_without_creating_ticket() -> None:
    response = client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": AUTH_TOKEN,
        },
    )

    assert response.status_code == 200
    assert "请确认：是否创建以下工单" in response.json()["answer"]
    assert has_java_pending_action() is True
    assert len(MOCK_TICKETS) == 5


def test_create_ticket_confirm_executes_and_clears_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": AUTH_TOKEN,
        },
    )

    response = client.post("/agent/chat", json={"message": "确认", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert "已创建工单：ID 6" in response.json()["answer"]
    assert has_java_pending_action() is False
    result = search_tickets.invoke({"keyword": "仍提示错误"})
    assert result["total"] == 1


def test_create_ticket_cancel_does_not_execute_and_clears_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": AUTH_TOKEN,
        },
    )

    response = client.post("/agent/chat", json={"message": "取消", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert response.json()["answer"] == "已取消创建工单，本次操作未执行。"
    assert has_java_pending_action() is False
    assert len(MOCK_TICKETS) == 5


def test_update_ticket_status_request_creates_pending_action_without_modifying() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 1 号工单改成处理中", "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "请确认：是否将 1 号工单状态修改为 PROCESSING" in response.json()["answer"]
    assert has_java_pending_action() is True
    assert find_ticket_by_id(1)["status"] == "OPEN"


def test_update_ticket_status_confirm_executes_and_clears_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": "把 1 号工单改成处理中", "auth_token": AUTH_TOKEN},
    )

    response = client.post("/agent/chat", json={"message": "确认", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert "已将 1 号工单状态从 OPEN 修改为 PROCESSING" in response.json()["answer"]
    assert has_java_pending_action() is False
    assert find_ticket_by_id(1)["status"] == "PROCESSING"
    result = search_tickets.invoke({"status": "PROCESSING"})
    assert 1 in {ticket["id"] for ticket in result["items"]}


def test_update_ticket_status_cancel_does_not_execute_and_clears_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": "把 1 号工单改成处理中", "auth_token": AUTH_TOKEN},
    )

    response = client.post("/agent/chat", json={"message": "取消", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert response.json()["answer"] == "已取消修改工单状态，本次操作未执行。"
    assert has_java_pending_action() is False
    assert find_ticket_by_id(1)["status"] == "OPEN"


def test_confirm_without_pending_action_does_not_execute_any_tool() -> None:
    before_count = len(MOCK_TICKETS)

    response = client.post("/agent/chat", json={"message": "确认", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert response.json()["answer"] == "当前没有待确认的操作，请重新发起请求。"
    assert len(MOCK_TICKETS) == before_count
    assert find_ticket_by_id(1)["status"] == "OPEN"


def test_cancel_without_pending_action_does_not_execute_any_tool() -> None:
    before_count = len(MOCK_TICKETS)

    response = client.post("/agent/chat", json={"message": "取消", "auth_token": AUTH_TOKEN})

    assert response.status_code == 200
    assert response.json()["answer"] == "当前会话没有待取消的操作。"
    assert len(MOCK_TICKETS) == before_count
    assert find_ticket_by_id(1)["status"] == "OPEN"


def test_create_ticket_missing_fields_does_not_save_pending_action() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "帮我创建一个登录问题工单"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "请补充工单标题、描述和优先级。"
    assert has_java_pending_action() is False


def test_update_ticket_status_missing_fields_does_not_save_pending_action() -> None:
    response = client.post("/agent/chat", json={"message": "把工单改成处理中"})

    assert response.status_code == 200
    assert "修改工单状态还缺少必要信息：工单 ID" in response.json()["answer"]
    assert has_java_pending_action() is False


def test_create_ticket_intent_does_not_call_direct_create_tool(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingCreateTool:
        def invoke(self, tool_input: dict) -> dict:
            raise AssertionError("direct create_ticket tool must not be called")

    monkeypatch.setattr(
        agent_api.agent_tool_service,
        "create_ticket_tool",
        FailingCreateTool(),
    )

    response = client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "请确认：是否创建以下工单" in response.json()["answer"]
    assert has_java_pending_action() is True
    assert len(MOCK_TICKETS) == 5


def test_update_status_intent_does_not_call_direct_update_tool(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingUpdateTool:
        def invoke(self, tool_input: dict) -> dict:
            raise AssertionError("direct update_ticket_status tool must not be called")

    monkeypatch.setattr(
        agent_api.agent_tool_service,
        "update_ticket_status_tool",
        FailingUpdateTool(),
    )

    response = client.post(
        "/agent/chat",
        json={"message": "把 1 号工单改成处理中", "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "请确认：是否将 1 号工单状态修改为 PROCESSING" in response.json()["answer"]
    assert has_java_pending_action() is True
    assert find_ticket_by_id(1)["status"] == "OPEN"


def test_update_ticket_status_ticket_not_found_does_not_save_pending_action() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 999 号工单改成处理中", "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "请确认：是否将 999 号工单状态修改为 PROCESSING" in response.json()["answer"]
    assert has_java_pending_action() is True

    confirm_response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": AUTH_TOKEN},
    )
    assert "目标工单不存在，或你无权访问该工单。" in confirm_response.json()["answer"]
    assert has_java_pending_action() is False


def test_update_ticket_status_invalid_transition_confirms_then_returns_error() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": "把 2 号工单改成未处理", "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "请确认：是否将 2 号工单状态修改为 OPEN" in response.json()["answer"]
    assert has_java_pending_action() is True

    confirm_response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": AUTH_TOKEN},
    )
    assert "工单状态流转不合法" in confirm_response.json()["answer"]
    assert has_java_pending_action() is False
    assert find_ticket_by_id(2)["status"] == "PROCESSING"


def test_pending_action_payload_does_not_save_token() -> None:
    response = client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    assert response.status_code == 200
    action = get_java_pending_action()
    assert action is not None
    assert action["status"] == "PENDING"
    assert "auth_token" not in action["payload"]
    assert "token" not in action["payload"]
    assert "Authorization" not in action["payload"]
    assert "authorization" not in action["payload"]


def test_confirm_without_token_fails_and_keeps_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    response = client.post("/agent/chat", json={"message": "确认"})

    assert response.status_code == 200
    assert response.json()["answer"] == FakeJavaTicketClient.LOGIN_EXPIRED_MESSAGE
    assert has_java_pending_action() is True
    assert len(MOCK_TICKETS) == 5


def test_confirm_with_tampered_token_fails_and_keeps_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": "TAMPERED_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == FakeJavaTicketClient.LOGIN_EXPIRED_MESSAGE
    assert has_java_pending_action() is True
    assert get_java_pending_action()["status"] == "PENDING"
    assert len(MOCK_TICKETS) == 5


def test_confirm_with_expired_token_fails_and_keeps_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": "EXPIRED_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == FakeJavaTicketClient.LOGIN_EXPIRED_MESSAGE
    assert has_java_pending_action() is True
    assert get_java_pending_action()["status"] == "PENDING"
    assert len(MOCK_TICKETS) == 5


def test_other_user_token_cannot_confirm_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": "B_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "当前没有待确认的操作，请重新发起请求。"
    assert has_java_pending_action(AUTH_TOKEN) is True
    assert has_java_pending_action("B_TOKEN") is False
    assert len(MOCK_TICKETS) == 5


def test_correct_user_new_token_can_confirm_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": NEW_AUTH_TOKEN},
    )

    assert response.status_code == 200
    assert "已创建工单：ID 6" in response.json()["answer"]
    assert has_java_pending_action(NEW_AUTH_TOKEN) is False
    assert len(MOCK_TICKETS) == 6


def test_confirm_success_then_repeat_confirm_does_not_execute_twice() -> None:
    client.post(
        "/agent/chat",
        json={"message": CREATE_TICKET_MESSAGE, "auth_token": AUTH_TOKEN},
    )

    first_response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": AUTH_TOKEN},
    )
    second_response = client.post(
        "/agent/chat",
        json={"message": "确认", "auth_token": AUTH_TOKEN},
    )

    assert "已创建工单：ID 6" in first_response.json()["answer"]
    assert second_response.json()["answer"] == "当前没有待确认的操作，请重新发起请求。"
    assert FakeJavaTicketClient.confirm_counts[("1", "default")] == 1
    assert len(MOCK_TICKETS) == 6


def test_user_pending_actions_do_not_overwrite_each_other() -> None:
    FakeJavaTicketClient.seen_tokens.clear()

    response_a = client.post(
        "/agent/chat",
        json={
            "user_id": "A",
            "message": "把 1 号工单改成处理中",
            "auth_token": "A_TOKEN",
        },
    )
    response_b = client.post(
        "/agent/chat",
        json={
            "user_id": "B",
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": "B_TOKEN",
        },
    )

    assert response_a.status_code == 200
    assert response_b.status_code == 200
    assert has_java_pending_action("A_TOKEN", "user-A") is True
    assert has_java_pending_action("B_TOKEN", "user-B") is True

    confirm_a = client.post(
        "/agent/chat",
        json={"user_id": "A", "message": "确认", "auth_token": "A_TOKEN"},
    )

    assert "已将 1 号工单状态从 OPEN 修改为 PROCESSING" in confirm_a.json()["answer"]
    assert find_ticket_by_id(1)["status"] == "PROCESSING"
    assert len(MOCK_TICKETS) == 5
    assert has_java_pending_action("A_TOKEN", "user-A") is False
    assert has_java_pending_action("B_TOKEN", "user-B") is True

    confirm_b = client.post(
        "/agent/chat",
        json={"user_id": "B", "message": "确认", "auth_token": "B_TOKEN"},
    )

    assert "已创建工单：ID 6" in confirm_b.json()["answer"]
    assert has_java_pending_action("B_TOKEN", "user-B") is False
    assert "A_TOKEN" in FakeJavaTicketClient.seen_tokens
    assert "B_TOKEN" in FakeJavaTicketClient.seen_tokens


def test_confirm_in_other_user_session_does_not_execute_pending_action() -> None:
    client.post(
        "/agent/chat",
        json={"user_id": "A", "message": "把 1 号工单改成处理中", "auth_token": "A_TOKEN"},
    )

    response = client.post(
        "/agent/chat",
        json={"user_id": "B", "message": "确认", "auth_token": "B_TOKEN"},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "当前没有待确认的操作，请重新发起请求。"
    assert find_ticket_by_id(1)["status"] == "OPEN"
    assert has_java_pending_action("A_TOKEN", "user-A") is True
    assert has_java_pending_action("B_TOKEN", "user-B") is False


def test_same_user_different_conversations_do_not_overwrite_each_other() -> None:
    client.post(
        "/agent/chat",
        json={
            "user_id": "1",
            "conversation_id": "chat-1",
            "message": "把 1 号工单改成处理中",
            "auth_token": AUTH_TOKEN,
        },
    )
    client.post(
        "/agent/chat",
        json={
            "user_id": "1",
            "conversation_id": "chat-2",
            "message": (
                "帮我创建一个工单，标题是登录失败，"
                "描述是用户输入正确密码后仍提示错误，优先级高"
            ),
            "auth_token": AUTH_TOKEN,
        },
    )

    response = client.post(
        "/agent/chat",
        json={
            "user_id": "1",
            "conversation_id": "chat-1",
            "message": "确认",
            "auth_token": AUTH_TOKEN,
        },
    )

    assert "已将 1 号工单状态从 OPEN 修改为 PROCESSING" in response.json()["answer"]
    assert find_ticket_by_id(1)["status"] == "PROCESSING"
    assert len(MOCK_TICKETS) == 5
    assert (
        has_java_pending_action(conversation_id="chat-1") is False
    )
    assert has_java_pending_action(conversation_id="chat-2") is True


def test_search_tickets_with_user_session_still_does_not_create_pending_action() -> None:
    response = client.post(
        "/agent/chat",
        json={"user_id": "searcher", "message": "查一下所有工单"},
    )

    assert response.status_code == 200
    assert "共找到 5 个工单" in response.json()["answer"]
    assert has_java_pending_action(conversation_id="user-searcher") is False


def test_normal_chat_still_falls_back_to_mock_llm() -> None:
    response = client.post("/agent/chat", json={"message": "你好"})

    assert response.status_code == 200
    assert response.json()["answer"] == UNKNOWN_INTENT_ANSWER
