from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import CategorySuggestionResult
from app.services.ai_capabilities.base import TicketAiCapabilityBase


class CategorySuggestionService(TicketAiCapabilityBase):
    CATEGORY_KEYWORDS = (
        ("账号登录", ("登录", "账号", "密码", "认证", "权限")),
        ("文件上传", ("上传", "下载", "文件", "附件", "PDF")),
        ("订单支付", ("订单", "支付", "退款", "待支付")),
        ("接口服务", ("接口", "API", "超时", "响应", "服务")),
        ("数据库", ("数据库", "SQL", "数据", "恢复")),
    )

    def suggest(
        self,
        auth_token: str | None = None,
        ticket_id: int | None = None,
        title: str | None = None,
        description: str | None = None,
    ) -> CategorySuggestionResult:
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
    ) -> CategorySuggestionResult:
        risk_flags = self.base_risk_flags(ticket_detail)
        if ticket_detail.category and ticket_detail.category != "OTHER":
            result = CategorySuggestionResult(
                suggested_category=ticket_detail.category,
                confidence=0.9 if not risk_flags else 0.6,
                reason="Java 工单详情中已经存在 category 字段，本阶段只返回建议，不修改工单表。",
                risk_flags=risk_flags,
            )
            result.risk_flags = self.grounding_service.add_unsupported_conclusion_flag(
                output_text=result.reason,
                ticket_detail=ticket_detail,
                risk_flags=result.risk_flags,
            )
            return result

        result = self._suggest_from_text(
            title=ticket_detail.title,
            description=ticket_detail.description or "",
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
    ) -> CategorySuggestionResult:
        risk_flags = []
        if not title and not description:
            risk_flags.append("信息不足")
        return self._suggest_from_text(
            title=title or "",
            description=description or "",
            risk_flags=risk_flags,
        )

    def _suggest_from_text(
        self,
        title: str,
        description: str,
        risk_flags: list[str],
    ) -> CategorySuggestionResult:
        text = f"{title} {description}".lower()
        for category, keywords in self.CATEGORY_KEYWORDS:
            if any(keyword.lower() in text for keyword in keywords):
                return CategorySuggestionResult(
                    suggested_category=category,
                    confidence=0.81 if not risk_flags else 0.6,
                    reason=f"工单标题或描述中出现了与“{category}”相关的关键词。",
                    risk_flags=risk_flags,
                )

        risk_flags = self.merge_risk_flags(risk_flags, ["信息不足", "需要人工确认"])
        return CategorySuggestionResult(
            suggested_category="其他",
            confidence=0.45,
            reason="工单信息不足，无法稳定判断分类；本阶段只返回建议，不修改 Java 表结构。",
            risk_flags=risk_flags,
        )
