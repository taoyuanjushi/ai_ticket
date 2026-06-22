from app.schemas.ticket_ai_schema import TicketSummaryResult
from app.services.ai_capabilities.base import TicketAiCapabilityBase


class TicketSummaryService(TicketAiCapabilityBase):
    def summarize(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketSummaryResult:
        ticket_detail = self.get_ticket_detail(auth_token=auth_token, ticket_id=ticket_id)
        risk_flags = self.base_risk_flags(ticket_detail)

        key_points = [
            f"标题：{ticket_detail.title}",
            f"状态：{ticket_detail.status.value}",
        ]
        if ticket_detail.priority:
            key_points.append(f"优先级：{ticket_detail.priority.value}")
        if ticket_detail.category:
            key_points.append(f"分类：{ticket_detail.category}")
        if ticket_detail.description:
            key_points.append(f"描述：{ticket_detail.description}")
        if ticket_detail.replies:
            key_points.append(f"历史回复数：{len(ticket_detail.replies)}")

        if risk_flags:
            return TicketSummaryResult(
                summary="当前工单信息不足，需要补充问题现象、操作步骤、错误截图、报错码、日志或处理结果后再继续总结。",
                key_points=key_points,
                risk_flags=risk_flags,
            )

        description = ticket_detail.description or "暂无描述"
        summary = (
            f"{ticket_detail.id}号工单《{ticket_detail.title}》当前状态为"
            f"{ticket_detail.status.value}"
        )
        if ticket_detail.priority:
            summary += f"，优先级为{ticket_detail.priority.value}"
        summary += f"。工单描述：{description}"
        if ticket_detail.replies:
            latest_reply = ticket_detail.replies[-1].content
            summary += f"。最近一次历史回复：{latest_reply}"
        summary += "。"

        return TicketSummaryResult(
            summary=summary,
            key_points=key_points,
            risk_flags=risk_flags,
        )
