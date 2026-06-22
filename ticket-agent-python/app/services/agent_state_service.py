from app.schemas.agent_state_schema import PendingAction
from app.services.pending_action_store import DEFAULT_SESSION_KEY, PendingActionStore


class AgentStateService:
    """Legacy in-memory state wrapper kept for local demos and old tests.

    The production Agent confirmation flow uses Java pending_action APIs. Do not
    use this service as the source of truth for real write confirmations.
    """

    def __init__(self, store: PendingActionStore | None = None) -> None:
        self._store = store or PendingActionStore()

    def set_pending_action(
        self,
        action: PendingAction | dict,
        session_key: str = DEFAULT_SESSION_KEY,
    ) -> None:
        self._store.set(session_key, action)

    def get_pending_action(
        self,
        session_key: str = DEFAULT_SESSION_KEY,
    ) -> PendingAction | None:
        return self._store.get(session_key)

    def clear_pending_action(self, session_key: str | None = None) -> None:
        if session_key is None:
            self._store.clear()
            return
        self._store.delete(session_key)

    def has_pending_action(self, session_key: str = DEFAULT_SESSION_KEY) -> bool:
        return self._store.has(session_key)
