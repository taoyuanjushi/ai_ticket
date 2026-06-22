import time

from app.schemas.agent_state_schema import PendingAction

DEFAULT_SESSION_KEY = "default"


def build_session_key(user_id: str | None, conversation_id: str | None) -> str:
    clean_conversation_id = conversation_id.strip() if conversation_id else None
    clean_user_id = user_id.strip() if user_id else None

    if clean_user_id and clean_conversation_id:
        return f"user:{clean_user_id}:conversation:{clean_conversation_id}"
    if clean_user_id:
        return f"user:{clean_user_id}"
    if clean_conversation_id:
        return f"conversation:{clean_conversation_id}"

    # Local learning fallback only. Production clients should pass user_id or conversation_id.
    return DEFAULT_SESSION_KEY


class InMemoryPendingActionStoreForTest:
    """
    In-memory pending action store for local learning and tests only.

    Production pending_action state is owned by the Java backend. This class is
    intentionally not used as the source of truth for /agent/chat write actions,
    because Python process memory is lost on restart and cannot safely isolate
    real multi-user confirmation state.
    """

    def __init__(self, expire_seconds: int = 300) -> None:
        self._store: dict[str, PendingAction] = {}
        self.expire_seconds = expire_seconds

    def set(self, session_key: str, action: PendingAction | dict) -> None:
        self._store[session_key] = (
            action if isinstance(action, PendingAction) else PendingAction(**action)
        )

    def get(self, session_key: str) -> PendingAction | None:
        action = self._store.get(session_key)
        if action is None:
            return None

        if self._is_expired(action):
            self.delete(session_key)
            return None

        return action

    def delete(self, session_key: str) -> None:
        self._store.pop(session_key, None)

    def has(self, session_key: str) -> bool:
        return self.get(session_key) is not None

    def clear(self) -> None:
        self._store.clear()

    def _is_expired(self, action: PendingAction) -> bool:
        return time.time() - action.created_at > self.expire_seconds


# Backward-compatible alias for older tests/imports. New code should use
# InMemoryPendingActionStoreForTest if it really needs an in-memory store.
PendingActionStore = InMemoryPendingActionStoreForTest
