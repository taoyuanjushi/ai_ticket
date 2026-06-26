from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, Field

from app.schemas.intent_schema import IntentType
from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import ReplySuggestionResult
from app.services.guardrail_service import GuardrailService

GROUNDING_RULES = """你只能使用 ticket_detail 中的信息。
不能编造 ticket_detail 中不存在的事实。
不能假设系统日志、监控数据、错误码、根因、处理结果、用户反馈、SLA 截止时间。
如果信息不足，请明确说明“信息不足”，并在 risk_flags 中加入“信息不足”。
如果缺少 SLA 字段，请在 missing_fields 中列出缺失字段，并在 risk_flags 中加入“SLA字段不足”。
必须只输出合法 JSON，不要输出 Markdown，不要输出解释性文字。"""

HIGH_RISK_PHRASES = (
    "已经修复",
    "已解决",
    "已完成处理",
    "已经确认",
    "数据库已恢复",
    "服务已恢复",
    "用户已解决",
    "问题已解决",
    "日志显示",
    "监控显示",
    "已定位根因",
    "根因是",
    "已定位原因",
    "用户已确认",
    "已经通知用户",
    "SLA 已超时",
    "SLA已超时",
    "还有 2 小时超时",
    "还有2小时超时",
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
UNSUPPORTED_CONCLUSION_FLAG = "可能包含未依据结论"
JAVA_SERVICE_ERROR_FLAG = "Java服务异常"
JAVA_CONNECTION_FAILED_FLAG = "Java连接失败"
JAVA_TIMEOUT_FLAG = "Java响应超时"
SLA_FIELDS_INSUFFICIENT_FLAG = "SLA字段不足"
STANDARD_INSUFFICIENT_SUGGESTION = (
    "当前工单信息不足，需要补充问题现象、操作步骤、错误截图、报错码、"
    "日志或处理结果后再继续判断。"
)
STANDARD_INSUFFICIENT_REASON = (
    "ticket_detail 中缺少支持该结论的明确信息，已降级为补充信息提示。"
)


class GroundingResult(BaseModel):
    ok: bool
    ticket_detail: dict[str, Any] | None = None
    error_response: dict[str, Any] | None = None
    missing_fields: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class TicketGroundingService:
    """Shared guardrails for AI output that depends on ticket detail."""

    def __init__(self, guardrail_service: GuardrailService | None = None) -> None:
        self.guardrail_service = guardrail_service or GuardrailService()

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
            self.build_ticket_context(ticket_detail),
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
1. 必须输出合法 JSON，且只能输出 JSON；不要输出 Markdown，不要输出解释文字，不要使用 ```json 代码块。
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
            return self.to_insufficient_result(
                result,
                additional_flags=[UNSUPPORTED_CONCLUSION_FLAG],
            )

        return result

    def get_ticket_grounding(
        self,
        ticket_id: int,
        auth_token: str | None,
        java_ticket_client: Any | None = None,
    ) -> GroundingResult:
        from app.clients.java_ticket_client import JavaApiError, JavaTicketClient

        client = java_ticket_client or JavaTicketClient()
        try:
            ticket_detail = client.get_ticket_detail(
                auth_token=auth_token,
                ticket_id=ticket_id,
            )
        except JavaApiError as exc:
            risk_flags = self.java_error_risk_flags(exc.status_code)
            return GroundingResult(
                ok=False,
                error_response=self.java_error_response(
                    status_code=exc.status_code,
                    message=exc.message,
                    risk_flags=risk_flags,
                ),
                risk_flags=risk_flags,
            )

        context = self.build_ticket_context(ticket_detail)
        risk_flags: list[str] = []
        if self.is_information_insufficient(ticket_detail):
            risk_flags = self._merge_flags(
                risk_flags,
                [INSUFFICIENT_INFORMATION_FLAG, HUMAN_REVIEW_FLAG],
            )
        return GroundingResult(
            ok=True,
            ticket_detail=context,
            missing_fields=self.missing_ticket_context_fields(context),
            risk_flags=risk_flags,
        )

    def build_ticket_context(
        self,
        ticket_detail: TicketDetailDTO | dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(ticket_detail, TicketDetailDTO):
            return self._safe_value(ticket_detail)

        context = self._drop_none(
            {
                "id": ticket_detail.id,
                "title": ticket_detail.title,
                "description": ticket_detail.description,
                "status": self._enum_value(ticket_detail.status),
                "priority": self._enum_value(ticket_detail.priority),
                "category": ticket_detail.category,
                "assignedTo": ticket_detail.assignedTo,
                "createdAt": ticket_detail.createdAt,
                "updatedAt": ticket_detail.updatedAt,
                "deadline": ticket_detail.deadline,
                "lastReplyAt": ticket_detail.lastReplyAt,
                "responseDueAt": ticket_detail.responseDueAt,
                "resolveDueAt": ticket_detail.resolveDueAt,
            }
        )
        context["replies"] = [
            self._drop_none(
                {
                    "content": reply.content,
                    "type": self._enum_value(reply.type),
                    "createdAt": reply.createdAt,
                }
            )
            for reply in ticket_detail.replies
        ]
        return self._safe_value(context)

    def missing_ticket_context_fields(self, context: dict[str, Any]) -> list[str]:
        missing_fields = []
        for field_name in ("description", "replies"):
            value = context.get(field_name)
            if value is None or value == "" or value == []:
                missing_fields.append(field_name)
        return missing_fields

    def java_error_risk_flags(self, status_code: int) -> list[str]:
        if status_code == 502:
            return [JAVA_CONNECTION_FAILED_FLAG]
        if status_code == 504:
            return [JAVA_TIMEOUT_FLAG]
        if status_code >= 500:
            return [JAVA_SERVICE_ERROR_FLAG]
        return []

    def java_error_response(
        self,
        status_code: int,
        message: str,
        risk_flags: list[str] | None = None,
    ) -> dict[str, Any]:
        response_type = "ERROR"
        if status_code == 401:
            response_type = "UNAUTHORIZED"
        elif status_code in (403, 404):
            response_type = "FORBIDDEN"
        return {
            "type": response_type,
            "message": message,
            "data": None,
            "risk_flags": risk_flags or self.java_error_risk_flags(status_code),
        }

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
        return self.guardrail_service.find_unsupported_high_risk_text(
            output_text=output_text,
            ticket_context=self.safe_ticket_detail(ticket_detail),
            high_risk_phrases=HIGH_RISK_PHRASES,
        )

    def add_unsupported_conclusion_flag(
        self,
        output_text: str,
        ticket_detail: TicketDetailDTO,
        risk_flags: list[str],
    ) -> list[str]:
        return self.guardrail_service.add_unsupported_conclusion_flag(
            output_text=output_text,
            ticket_context=self.safe_ticket_detail(ticket_detail),
            risk_flags=risk_flags,
            high_risk_phrases=HIGH_RISK_PHRASES,
            unsupported_flag=UNSUPPORTED_CONCLUSION_FLAG,
        )

    def to_insufficient_result(
        self,
        result: ReplySuggestionResult | None = None,
        additional_flags: list[str] | None = None,
    ) -> ReplySuggestionResult:
        risk_flags = self._merge_flags(
            result.risk_flags if result else [],
            [INSUFFICIENT_INFORMATION_FLAG, HUMAN_REVIEW_FLAG],
        )
        if additional_flags:
            risk_flags = self._merge_flags(risk_flags, additional_flags)
        confidence = min(result.confidence, 0.6) if result else 0.4
        return ReplySuggestionResult(
            suggestion=STANDARD_INSUFFICIENT_SUGGESTION,
            confidence=confidence,
            reason=STANDARD_INSUFFICIENT_REASON,
            risk_flags=risk_flags,
        )

    def safe_ticket_detail(self, ticket_detail: TicketDetailDTO) -> dict[str, Any]:
        return self._safe_value(ticket_detail.model_dump(mode="json", exclude_none=True))

    def _enum_value(self, value: Any) -> Any:
        return getattr(value, "value", value)

    def _drop_none(self, value: dict[str, Any]) -> dict[str, Any]:
        return {key: item for key, item in value.items() if item is not None}

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
