from typing import Any

import httpx

from app.core.config import Settings, settings
from app.core.exceptions import AppException, LLM_CALL_FAILED
from app.core.logger import get_logger

logger = get_logger(__name__)

MOCK_ANSWER = "我是智能工单助手，可以帮你查询、创建、修改和总结工单。"
SYSTEM_PROMPT = (
    "你是一个智能工单助手。当前阶段你只能介绍自己的能力，"
    "不要真的创建、查询或修改工单。"
)


class LLMService:
    def __init__(self, app_settings: Settings = settings) -> None:
        self.settings = app_settings

    async def chat(self, message: str) -> str:
        if not self.settings.has_llm_config:
            return MOCK_ANSWER

        try:
            base_url = self.settings.resolved_llm_api_base_url.rstrip("/")
            response_data = await self._call_llm(base_url, message)
            return self._extract_answer(response_data)
        except AppException:
            raise
        except (httpx.HTTPError, ValueError, KeyError, IndexError, TypeError) as exc:
            logger.exception("LLM call failed")
            raise AppException(
                code=LLM_CALL_FAILED,
                message="模型服务调用失败，请稍后重试",
                status_code=502,
            ) from exc

    async def _call_llm(self, base_url: str, message: str) -> dict[str, Any]:
        url = f"{base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.settings.llm_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.settings.llm_model,
            "messages": [
                {
                    "role": "system",
                    "content": SYSTEM_PROMPT,
                },
                {
                    "role": "user",
                    "content": message,
                },
            ],
            "temperature": 0.3,
        }

        timeout = httpx.Timeout(self.settings.llm_timeout)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(url, headers=headers, json=payload)
            response.raise_for_status()
            return response.json()

    def _extract_answer(self, response_data: dict[str, Any]) -> str:
        content = response_data["choices"][0]["message"]["content"]
        if not isinstance(content, str) or not content.strip():
            raise ValueError("LLM response content is empty")
        return content.strip()
