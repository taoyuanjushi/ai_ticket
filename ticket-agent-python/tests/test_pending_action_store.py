import time

from app.schemas.agent_state_schema import PendingAction
from app.services.pending_action_store import (
    DEFAULT_SESSION_KEY,
    InMemoryPendingActionStoreForTest,
    PendingActionStore,
    build_session_key,
)


def make_pending_action(created_at: float | None = None) -> PendingAction:
    kwargs = {}
    if created_at is not None:
        kwargs["created_at"] = created_at
    return PendingAction(
        tool_name="update_ticket_status",
        args={"ticket_id": 1, "status": "PROCESSING"},
        summary="将 1 号工单状态修改为 PROCESSING",
        **kwargs,
    )


def test_build_session_key_uses_user_and_conversation_id() -> None:
    assert build_session_key("user-1", "chat-1") == "user:user-1:conversation:chat-1"


def test_build_session_key_uses_user_id_when_conversation_id_missing() -> None:
    assert build_session_key("user-1", None) == "user:user-1"


def test_build_session_key_uses_conversation_id_when_user_id_missing() -> None:
    assert build_session_key(None, "chat-1") == "conversation:chat-1"


def test_build_session_key_uses_default_for_local_testing() -> None:
    assert build_session_key(None, None) == DEFAULT_SESSION_KEY


def test_build_session_key_strips_blank_values() -> None:
    assert build_session_key(" user-1 ", "   ") == "user:user-1"


def test_pending_action_store_isolates_actions_by_session_key() -> None:
    store = PendingActionStore()
    action_a = make_pending_action()
    action_b = PendingAction(
        tool_name="create_ticket",
        args={"title": "登录失败", "description": "无法登录", "priority": "HIGH"},
        summary="创建工单：登录失败，优先级 HIGH",
    )

    store.set("user:A", action_a)
    store.set("user:B", action_b)

    assert store.get("user:A") == action_a
    assert store.get("user:B") == action_b


def test_pending_action_store_name_marks_memory_store_as_test_only() -> None:
    store = InMemoryPendingActionStoreForTest()

    assert isinstance(store, PendingActionStore)


def test_pending_action_store_delete_only_removes_current_session() -> None:
    store = PendingActionStore()
    store.set("user:A", make_pending_action())
    store.set("user:B", make_pending_action())

    store.delete("user:A")

    assert store.has("user:A") is False
    assert store.has("user:B") is True


def test_pending_action_store_expires_old_action() -> None:
    store = PendingActionStore(expire_seconds=1)
    store.set("user:A", make_pending_action(created_at=time.time() - 2))

    assert store.get("user:A") is None
    assert store.has("user:A") is False
