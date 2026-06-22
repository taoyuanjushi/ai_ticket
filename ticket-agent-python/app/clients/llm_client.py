from typing import Any
import json

import httpx

from app.core.config import Settings, settings

SYSTEM_PROMPT = "你是企业工单系统中的客服助手，只负责生成回复建议，不执行任何业务操作。"
MOCK_REPLY_SUGGESTION = (
    "建议先向用户确认问题是否仍然存在，并请用户提供相关错误截图或具体操作步骤，"
    "以便进一步定位原因。"
)


class LLMError(Exception):
    """LLM 调用异常。"""


class LLMClient:
    """LLM 客户端，负责调用 openai-compatible 模型服务。"""

    def __init__(self, app_settings: Settings = settings) -> None:
        self.settings = app_settings

    def generate_text(self, prompt: str) -> str:
        if self.settings.llm_mock_mode:
            return self._mock_generate_text(prompt)

        if not self._has_required_config():
            raise LLMError("LLM 配置不完整，请检查 LLM_API_KEY、LLM_API_BASE_URL、LLM_MODEL")

        return self._call_openai_compatible_api(prompt)

    def _has_required_config(self) -> bool:
        return all(
            [
                self.settings.llm_api_key.strip(),
                self.settings.resolved_llm_api_base_url,
                self.settings.llm_model.strip(),
            ]
        )

    def _mock_generate_text(self, prompt: str) -> str:
        return json.dumps(
            {
                "suggestion": MOCK_REPLY_SUGGESTION,
                "confidence": 0.8,
                "reason": "基于工单标题、描述、状态、优先级和历史回复生成。",
                "risk_flags": ["需要人工确认"],
            },
            ensure_ascii=False,
        )

    def _call_openai_compatible_api(self, prompt: str) -> str:
        url = self.settings.resolved_llm_api_base_url.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.settings.llm_api_key}",
            "Content-Type": "application/json",
        }
        body: dict[str, Any] = {
            "model": self.settings.llm_model,
            "messages": [
                {
                    "role": "system",
                    "content": SYSTEM_PROMPT,
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            "temperature": 0.3,
        }

        try:
            with httpx.Client(timeout=self.settings.resolved_llm_timeout) as client:
                response = client.post(url, headers=headers, json=body)
        except httpx.RequestError as exc:
            raise LLMError(f"LLM 服务连接失败：{exc}") from exc

        if not 200 <= response.status_code < 300:
            raise LLMError(f"LLM 服务调用失败：HTTP {response.status_code}")

        try:
            payload = response.json()
            content = payload["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            raise LLMError("LLM 返回格式不符合预期") from exc

        if not isinstance(content, str) or not content.strip():
            raise LLMError("LLM 返回内容为空")
        return content.strip()
