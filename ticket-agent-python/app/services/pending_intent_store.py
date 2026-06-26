from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from app.schemas.pending_intent import PendingIntent

DEFAULT_PENDING_INTENT_TTL_SECONDS = 10 * 60
DEFAULT_PENDING_INTENT_USER = "anonymous"
DEFAULT_PENDING_INTENT_CONVERSATION = "default"
PENDING_INTENT_KEY_PREFIX = "ai:pending_intent"


def build_pending_intent_key(
    user_id: str | None,
    conversation_id: str | None,
) -> str:
    normalized_user_id = str(user_id or DEFAULT_PENDING_INTENT_USER).strip()
    normalized_conversation_id = str(
        conversation_id or DEFAULT_PENDING_INTENT_CONVERSATION
    ).strip()
    return (
        f"{PENDING_INTENT_KEY_PREFIX}:"
        f"{normalized_user_id or DEFAULT_PENDING_INTENT_USER}:"
        f"{normalized_conversation_id or DEFAULT_PENDING_INTENT_CONVERSATION}"
    )


class InMemoryPendingIntentStoreForDev:
    """Development-only pending_intent store.

    pending_intent only stores unfinished intent parameters such as title,
    description, priority, ticket_id, and target_status. It must never store
    Authorization or token values. Replace this with Redis or Java-managed
    state if multiple Python workers are deployed.
    """

    def __init__(self, ttl_seconds: int = DEFAULT_PENDING_INTENT_TTL_SECONDS) -> None:
        self.ttl_seconds = ttl_seconds
        self._store: dict[str, PendingIntent] = {}

    def get(
        self,
        user_id_or_key: str | None,
        conversation_id: str | None = None,
    ) -> PendingIntent | None:
        key = self._resolve_key(user_id_or_key, conversation_id)
        item = self._store.get(key)
        if item is None:
            return None
        if self._is_expired(item):
            self.delete(key)
            return None
        return item

    def save(self, pending_intent: PendingIntent) -> None:
        self._store[
            build_pending_intent_key(
                pending_intent.user_id,
                pending_intent.conversation_id,
            )
        ] = self._sanitize(pending_intent)

    def set(self, key: str, pending_intent: PendingIntent) -> None:
        """Backward-compatible key-based setter used by older code/tests."""

        user_id, conversation_id = self._split_key(key)
        self.save(
            PendingIntent(
                user_id=pending_intent.user_id or user_id,
                conversation_id=pending_intent.conversation_id or conversation_id,
                intent=pending_intent.intent,
                collected=pending_intent.collected,
                missing_fields=list(pending_intent.missing_fields),
                created_at=pending_intent.created_at,
                updated_at=pending_intent.updated_at,
            )
        )

    def merge_fields(
        self,
        user_id: str,
        conversation_id: str,
        new_fields: dict[str, Any],
    ) -> PendingIntent | None:
        pending_intent = self.get(user_id, conversation_id)
        if pending_intent is None:
            return None

        collected = pending_intent.collected.copy()
        for key, value in self._strip_sensitive_fields(new_fields).items():
            if value is not None and value != "":
                collected[key] = value

        merged = PendingIntent(
            user_id=pending_intent.user_id,
            conversation_id=pending_intent.conversation_id,
            intent=pending_intent.intent,
            collected=collected,
            missing_fields=list(pending_intent.missing_fields),
            created_at=pending_intent.created_at,
            updated_at=self._now(),
        )
        self.save(merged)
        return merged

    def delete(self, key: str) -> None:
        self._store.pop(key, None)

    def clear(
        self,
        user_id: str | None = None,
        conversation_id: str | None = None,
    ) -> None:
        if user_id is None and conversation_id is None:
            self._store.clear()
            return
        self.delete(build_pending_intent_key(user_id, conversation_id))

    def _is_expired(self, pending_intent: PendingIntent) -> bool:
        return (self._now() - pending_intent.updated_at).total_seconds() > self.ttl_seconds

    def _resolve_key(
        self,
        user_id_or_key: str | None,
        conversation_id: str | None,
    ) -> str:
        if conversation_id is None and self._looks_like_key(user_id_or_key):
            return str(user_id_or_key)
        return build_pending_intent_key(user_id_or_key, conversation_id)

    def _looks_like_key(self, value: str | None) -> bool:
        return isinstance(value, str) and value.startswith(f"{PENDING_INTENT_KEY_PREFIX}:")

    def _split_key(self, key: str) -> tuple[str, str]:
        parts = key.split(":", maxsplit=3)
        if len(parts) == 4 and parts[0] == "ai" and parts[1] == "pending_intent":
            return parts[2], parts[3]
        return DEFAULT_PENDING_INTENT_USER, DEFAULT_PENDING_INTENT_CONVERSATION

    def _sanitize(self, pending_intent: PendingIntent) -> PendingIntent:
        return PendingIntent(
            user_id=pending_intent.user_id,
            conversation_id=pending_intent.conversation_id,
            intent=pending_intent.intent,
            collected=self._strip_sensitive_fields(pending_intent.collected),
            missing_fields=list(pending_intent.missing_fields),
            created_at=pending_intent.created_at,
            updated_at=pending_intent.updated_at,
        )

    def _strip_sensitive_fields(self, values: dict[str, Any]) -> dict[str, Any]:
        safe_values: dict[str, Any] = {}
        for key, value in values.items():
            normalized_key = key.strip().lower()
            if (
                "token" in normalized_key
                or normalized_key == "authorization"
                or "jwt" in normalized_key
                or "password" in normalized_key
            ):
                continue
            if isinstance(value, dict):
                safe_values[key] = self._strip_sensitive_fields(value)
            else:
                safe_values[key] = value
        return safe_values

    def _now(self) -> datetime:
        return datetime.now(timezone.utc)
