from app.schemas.ticket_ai_schema import SlaRiskLevel, SlaRiskResult
from app.services.ai_capabilities.base import TicketAiCapabilityBase
from app.services.grounding import SLA_FIELDS_INSUFFICIENT_FLAG


class SlaRiskService(TicketAiCapabilityBase):
    SLA_FIELDS = (
        "status",
        "responseDueAt",
        "resolveDueAt",
        "slaStatus",
        "slaOverdue",
        "slaRemainingMinutes",
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

        sla_status = getattr(ticket_detail.slaStatus, "value", ticket_detail.slaStatus)
        if sla_status == "OVERDUE":
            risk_level = SlaRiskLevel.HIGH
            reason = "Java 返回 slaStatus=OVERDUE，该工单已超过 SLA 解决截止时间。"
            risk_flags = self.merge_risk_flags(risk_flags, ["该工单已超过 SLA 解决截止时间"])
        elif sla_status == "AT_RISK":
            risk_level = SlaRiskLevel.MEDIUM
            reason = "Java 返回 slaStatus=AT_RISK，该工单接近 SLA 解决截止时间。"
            risk_flags = self.merge_risk_flags(risk_flags, ["该工单接近 SLA 解决截止时间"])
        elif sla_status == "COMPLETED":
            risk_level = SlaRiskLevel.LOW
            reason = "Java 返回 slaStatus=COMPLETED，工单已完成或关闭，当前无 SLA 超时风险。"
        elif sla_status == "ON_TRACK":
            risk_level = SlaRiskLevel.LOW
            reason = "Java 返回 slaStatus=ON_TRACK，当前未发现 SLA 超时风险。"
        elif sla_status == "NO_SLA":
            risk_level = SlaRiskLevel.UNKNOWN
            reason = "Java 返回 slaStatus=NO_SLA，系统未设置 SLA 截止时间，不能判断超时风险。"
            risk_flags = self.merge_risk_flags(risk_flags, [SLA_FIELDS_INSUFFICIENT_FLAG])
        else:
            risk_level = SlaRiskLevel.UNKNOWN
            reason = "Java 返回的 slaStatus 无法识别，不能判断 SLA 风险。"
            risk_flags = self.merge_risk_flags(risk_flags, [SLA_FIELDS_INSUFFICIENT_FLAG])

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
