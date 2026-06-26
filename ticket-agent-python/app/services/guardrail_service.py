from __future__ import annotations

import json
from collections.abc import Iterable
from typing import Any


class GuardrailService:
    """Pure output guardrails.

    This service only checks generated text against already-provided ticket
    context. It must not call Java, call LLMs, read pending_action, or build API
    responses.
    """

    def find_unsupported_high_risk_text(
        self,
        output_text: str,
        ticket_context: dict[str, Any],
        high_risk_phrases: Iterable[str],
    ) -> list[str]:
        detail_text = json.dumps(
            self._safe_value(ticket_context),
            ensure_ascii=False,
            sort_keys=True,
        )
        return [
            phrase
            for phrase in high_risk_phrases
            if phrase in output_text and phrase not in detail_text
        ]

    def add_unsupported_conclusion_flag(
        self,
        output_text: str,
        ticket_context: dict[str, Any],
        risk_flags: list[str],
        high_risk_phrases: Iterable[str],
        unsupported_flag: str,
    ) -> list[str]:
        unsupported_phrases = self.find_unsupported_high_risk_text(
            output_text=output_text,
            ticket_context=ticket_context,
            high_risk_phrases=high_risk_phrases,
        )
        if not unsupported_phrases:
            return list(risk_flags)
        return self.merge_risk_flags(risk_flags, [unsupported_flag])

    def merge_risk_flags(
        self,
        existing_flags: list[str],
        required_flags: list[str],
    ) -> list[str]:
        merged = list(existing_flags)
        for flag in required_flags:
            if flag not in merged:
                merged.append(flag)
        return merged

    def _safe_value(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: self._safe_value(item)
                for key, item in value.items()
                if str(key).lower() not in {"password", "token", "authorization", "auth_token"}
            }
        if isinstance(value, list):
            return [self._safe_value(item) for item in value]
        return value
