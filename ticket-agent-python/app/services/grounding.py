from __future__ import annotations

import json
from typing import Any

from app.schemas.intent_schema import IntentType
from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import ReplySuggestionResult

GROUNDING_RULES = """你只能使用 ticket_detail 中的信息。
不能使用外部知识编造工单不存在的事实。
如果 ticket_detail 中没有足够信息，请明确说“当前工单信息不足，需要补充……”
不能假设用户已经做过某些操作。
不能假设系统日志、报错码、处理结果，除非 ticket_detail 中明确出现。"""

HIGH_RISK_PHRASES = (
    "已经修复",
    "已经确认",
    "数据库已恢复",
    "用户已解决",
    "问题已解决",
    "已解决",
    "日志显示",
    "监控显示",
    "已定位原因",
    "已完成处理",
)

GROUNDING_REQUIRED_INTENTS = {
    IntentType.REPLY_SUGGESTION.value,
    IntentType.TICKET_SUMMARY.value,
    IntentType.PRIORITY_SUGGESTION.value,
    IntentType.CATEGORY_SUGGESTION.value,
    IntentType.SIMILAR_TICKET_SEARCH.value,
    IntentType.SLA_RISK_CHECK.value,
}

GROUNDING_REQUIRED_TASKS = {
    "回复建议",
    "工单摘要",
    "优先级建议",
    "分类建议",
    "相似工单检索",
    "SLA风险提醒",
    "工单内容查询",
    "处理建议",
}

INSUFFICIENT_INFORMATION_FLAG = "信息不足"
HUMAN_REVIEW_FLAG = "需要人工确认"
STANDARD_INSUFFICIENT_SUGGESTION = (
    "当前工单信息不足，需要补充问题现象、操作步骤、错误截图、报错码、"
    "日志或处理结果后再继续判断。"
)
STANDARD_INSUFFICIENT_REASON = (
    "ticket_detail 中缺少支持该结论的明确信息，已降级为补充信息提示。"
)


class TicketGroundingService:
    """Shared guardrails for AI output that depends on ticket detail."""

    def requires_ticket_detail(self, intent_or_task: IntentType | str) -> bool:
        if isinstance(intent_or_task, IntentType):
            value = intent_or_task.value
        else:
            value = str(intent_or_task)
        return value in GROUNDING_REQUIRED_INTENTS or value in GROUNDING_REQUIRED_TASKS

    def build_grounding_prompt(
        self,
        ticket_detail: TicketDetailDTO,
        task_name: str,
        task_instruction: str,
    ) -> str:
        detail_json = json.dumps(
            self.safe_ticket_detail(ticket_detail),
            ensure_ascii=False,
            indent=2,
        )
        return f"""
你是企业工单系统中的 AI 助手。当前任务：{task_name}。

防幻觉规则：
{GROUNDING_RULES}

任务要求：
{task_instruction}

输出要求：
1. 必须输出合法 JSON，不要输出 Markdown，不要输出解释文字。
2. JSON 必须包含 suggestion、confidence、reason、risk_flags。
3. confidence 必须是 0.0 到 1.0 之间的小数。
4. risk_flags 建议从以下值中选择：信息不足、需要人工确认、涉及权限问题、涉及敏感信息、可能需要升级处理、SLA风险。

ticket_detail JSON：
{detail_json}

输出格式必须严格符合：
{{
  "suggestion": "...",
  "confidence": 0.8,
  "reason": "...",
  "risk_flags": []
}}
""".strip()

    def apply_grounding(
        self,
        result: ReplySuggestionResult,
        ticket_detail: TicketDetailDTO,
        task_name: str = "回复建议",
    ) -> ReplySuggestionResult:
        if self.is_information_insufficient(ticket_detail):
            return self.to_insufficient_result(result)

        unsupported_phrases = self.find_unsupported_high_risk_phrases(
            result=result,
            ticket_detail=ticket_detail,
        )
        if unsupported_phrases:
            return self.to_insufficient_result(result)

        return result

    def is_information_insufficient(self, ticket_detail: TicketDetailDTO) -> bool:
        description = (ticket_detail.description or "").strip()
        has_meaningful_description = len(description) >= 8
        has_replies = bool(ticket_detail.replies)
        return not has_meaningful_description and not has_replies

    def find_unsupported_high_risk_phrases(
        self,
        result: ReplySuggestionResult,
        ticket_detail: TicketDetailDTO,
    ) -> list[str]:
        output_text = f"{result.suggestion}\n{result.reason}"
        return self.find_unsupported_high_risk_text(output_text, ticket_detail)

    def find_unsupported_high_risk_text(
        self,
        output_text: str,
        ticket_detail: TicketDetailDTO,
    ) -> list[str]:
        detail_text = json.dumps(
            self.safe_ticket_detail(ticket_detail),
            ensure_ascii=False,
            sort_keys=True,
        )
        return [
            phrase
            for phrase in HIGH_RISK_PHRASES
            if phrase in output_text and phrase not in detail_text
        ]

    def to_insufficient_result(
        self,
        result: ReplySuggestionResult | None = None,
    ) -> ReplySuggestionResult:
        risk_flags = self._merge_flags(
            result.risk_flags if result else [],
            [INSUFFICIENT_INFORMATION_FLAG, HUMAN_REVIEW_FLAG],
        )
        confidence = min(result.confidence, 0.6) if result else 0.4
        return ReplySuggestionResult(
            suggestion=STANDARD_INSUFFICIENT_SUGGESTION,
            confidence=confidence,
            reason=STANDARD_INSUFFICIENT_REASON,
            risk_flags=risk_flags,
        )

    def safe_ticket_detail(self, ticket_detail: TicketDetailDTO) -> dict[str, Any]:
        return self._safe_value(ticket_detail.model_dump(mode="json", exclude_none=True))

    def _safe_value(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: self._safe_value(item)
                for key, item in value.items()
                if str(key).lower() != "password"
            }
        if isinstance(value, list):
            return [self._safe_value(item) for item in value]
        return value

    def _merge_flags(
        self,
        existing_flags: list[str],
        required_flags: list[str],
    ) -> list[str]:
        merged = list(existing_flags)
        for flag in required_flags:
            if flag not in merged:
                merged.append(flag)
        return merged
