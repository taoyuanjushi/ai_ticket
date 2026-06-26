from app.schemas.ticket import TicketStatus
from app.schemas.ticket_ai_schema import SlaRiskLevel, SlaRiskResult
from app.services.ai_capabilities.base import TicketAiCapabilityBase
from app.services.grounding import SLA_FIELDS_INSUFFICIENT_FLAG


class SlaRiskService(TicketAiCapabilityBase):
    SLA_FIELDS = (
        "deadline",
        "createdAt",
        "updatedAt",
        "priority",
        "status",
        "lastReplyAt",
        "responseDueAt",
        "resolveDueAt",
    )

    def check(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> SlaRiskResult:
        ticket_detail = self.get_ticket_detail(auth_token=auth_token, ticket_id=ticket_id)
        risk_flags = self.base_risk_flags(ticket_detail)
        missing_fields = self._missing_sla_fields(ticket_detail)
        if missing_fields:
            risk_flags = self.merge_risk_flags(risk_flags, [SLA_FIELDS_INSUFFICIENT_FLAG])
            return SlaRiskResult(
                sla_risk_level=SlaRiskLevel.UNKNOWN,
                reason=(
                    "当前工单缺少 "
                    + "、".join(missing_fields)
                    + " 等 SLA 判断字段，无法判断精确风险，不能编造截止时间或超时结论。"
                ),
                missing_fields=missing_fields,
                risk_flags=risk_flags,
            )

        if ticket_detail.status == TicketStatus.CLOSED:
            risk_level = SlaRiskLevel.LOW
            reason = "工单当前状态为 CLOSED，暂按低 SLA 风险处理。"
        elif ticket_detail.priority and ticket_detail.priority.value == "HIGH":
            risk_level = SlaRiskLevel.MEDIUM
            reason = "高优先级工单仍未关闭，可能需要尽快处理。"
        elif ticket_detail.priority and ticket_detail.priority.value == "URGENT":
            risk_level = SlaRiskLevel.HIGH
            reason = "紧急优先级工单仍未关闭，存在较高 SLA 风险。"
        else:
            risk_level = SlaRiskLevel.LOW
            reason = "基于当前状态和优先级，暂未发现明确 SLA 风险。"
        risk_flags = self.grounding_service.add_unsupported_conclusion_flag(
            output_text=reason,
            ticket_detail=ticket_detail,
            risk_flags=risk_flags,
        )

        return SlaRiskResult(
            sla_risk_level=risk_level,
            reason=reason,
            missing_fields=missing_fields,
            risk_flags=risk_flags,
        )

    def _missing_sla_fields(self, ticket_detail) -> list[str]:
        missing_fields = []
        for field_name in self.SLA_FIELDS:
            value = getattr(ticket_detail, field_name, None)
            if value is None or value == "":
                missing_fields.append(field_name)
        return missing_fields
