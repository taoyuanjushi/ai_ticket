import json
import re
from typing import Any


class LLMJsonParseError(ValueError):
    pass


def parse_llm_json(raw_text: str) -> dict[str, Any]:
    text = str(raw_text or "").strip()
    for candidate in (text, _strip_markdown_code_fence(text), _extract_json_object(text)):
        if not candidate:
            continue
        try:
            payload = json.loads(candidate)
        except ValueError:
            continue
        if isinstance(payload, dict):
            return payload
        raise LLMJsonParseError("LLM JSON output must be an object")

    raise LLMJsonParseError("LLM output is not valid JSON")


def _strip_markdown_code_fence(text: str) -> str:
    stripped = text.strip()
    stripped = re.sub(r"^```(?:json)?\s*", "", stripped, flags=re.IGNORECASE)
    stripped = re.sub(r"\s*```$", "", stripped)
    return stripped.strip()


def _extract_json_object(text: str) -> str:
    stripped = _strip_markdown_code_fence(text)
    start = stripped.find("{")
    end = stripped.rfind("}")
    if start == -1 or end <= start:
        return ""
    return stripped[start : end + 1].strip()
