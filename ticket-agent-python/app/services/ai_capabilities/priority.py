from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import (
    PrioritySuggestionResult,
    SuggestedPriority,
)
from app.services.ai_capabilities.base import TicketAiCapabilityBase


class PrioritySuggestionService(TicketAiCapabilityBase):
    HIGH_KEYWORDS = (
        "无法登录",
        "登录失败",
        "支付",
        "订单",
        "超时",
        "阻塞",
        "无法",
        "失败",
        "报错",
        "崩溃",
        "紧急",
        "生产",
    )
    MEDIUM_KEYWORDS = (
        "异常",
        "上传",
        "接口",
        "卡顿",
        "变慢",
        "失败",
    )
    LOW_KEYWORDS = (
        "归档",
        "咨询",
        "建议",
        "优化",
        "文案",
    )

    def suggest(
        self,
        auth_token: str | None = None,
        ticket_id: int | None = None,
        title: str | None = None,
        description: str | None = None,
    ) -> PrioritySuggestionResult:
        if ticket_id is not None:
            ticket_detail = self.get_ticket_detail(
                auth_token=auth_token,
                ticket_id=ticket_id,
            )
            return self.suggest_from_ticket_detail(ticket_detail)

        return self.suggest_from_text(title=title, description=description)

    def suggest_from_ticket_detail(
        self,
        ticket_detail: TicketDetailDTO,
    ) -> PrioritySuggestionResult:
        risk_flags = self.base_risk_flags(ticket_detail)
        result = self._suggest_from_text(
            title=ticket_detail.title,
            description=ticket_detail.description or "",
            current_priority=ticket_detail.priority.value if ticket_detail.priority else None,
            risk_flags=risk_flags,
        )
        result.risk_flags = self.grounding_service.add_unsupported_conclusion_flag(
            output_text=result.reason,
            ticket_detail=ticket_detail,
            risk_flags=result.risk_flags,
        )
        return result

    def suggest_from_text(
        self,
        title: str | None,
        description: str | None,
    ) -> PrioritySuggestionResult:
        risk_flags = []
        if not title and not description:
            risk_flags.append("信息不足")
        return self._suggest_from_text(
            title=title or "",
            description=description or "",
            current_priority=None,
            risk_flags=risk_flags,
        )

    def _suggest_from_text(
        self,
        title: str,
        description: str,
        current_priority: str | None,
        risk_flags: list[str],
    ) -> PrioritySuggestionResult:
        text = f"{title} {description}".lower()
        if any(keyword.lower() in text for keyword in self.HIGH_KEYWORDS):
            suggested_priority = SuggestedPriority.HIGH
            confidence = 0.76
            reason = "工单内容包含登录失败、支付、订单、超时、阻塞或严重失败等高影响关键词。"
        elif any(keyword.lower() in text for keyword in self.LOW_KEYWORDS):
            suggested_priority = SuggestedPriority.LOW
            confidence = 0.68
            reason = "工单内容更偏向咨询、归档、优化或非阻塞类事项。"
        elif any(keyword.lower() in text for keyword in self.MEDIUM_KEYWORDS):
            suggested_priority = SuggestedPriority.MEDIUM
            confidence = 0.66
            reason = "工单内容包含异常、上传、接口或性能相关关键词，但缺少更高影响范围依据。"
        else:
            suggested_priority = SuggestedPriority.MEDIUM
            confidence = 0.45
            reason = "工单信息不足以明确判断影响范围和紧急程度，先给出中等优先级建议。"
            risk_flags = self.merge_risk_flags(risk_flags, ["信息不足", "需要人工确认"])

        if current_priority == "URGENT":
            reason += " 当前 Java 工单优先级为 URGENT，但本阶段 AI 建议只输出 LOW、MEDIUM、HIGH，因此按 HIGH 处理。"
            suggested_priority = SuggestedPriority.HIGH

        if risk_flags:
            confidence = min(confidence, 0.6)

        return PrioritySuggestionResult(
            suggested_priority=suggested_priority,
            confidence=confidence,
            reason=reason,
            risk_flags=risk_flags,
        )
