from typing import Any
import json

from app.schemas.ticket import TicketDetailDTO
from app.services.grounding import GROUNDING_RULES, TicketGroundingService


def build_reply_suggestion_prompt(ticket_detail: dict[str, Any] | TicketDetailDTO) -> str:
    if isinstance(ticket_detail, TicketDetailDTO):
        detail_payload = TicketGroundingService().build_ticket_context(ticket_detail)
        ticket = detail_payload
        replies = detail_payload.get("replies", [])
    else:
        raw_ticket = _safe_dict(ticket_detail.get("ticket"))
        replies = ticket_detail.get("replies", [])
        detail_payload = {
            "ticket": raw_ticket,
            "replies": replies if isinstance(replies, list) else [],
        }
        ticket = raw_ticket
    if not isinstance(replies, list):
        replies = []

    title = _text(ticket.get("title"))
    content = _text(ticket.get("content") or ticket.get("description"))
    status = _text(ticket.get("status"))
    priority = _text(ticket.get("priority"))
    category = _text(ticket.get("category"))
    replies_text = _format_replies(replies)
    detail_json = json.dumps(_safe_value(detail_payload), ensure_ascii=False, indent=2)

    return f"""
你是企业工单系统中的客服助手。请基于以下真实工单信息，生成给客服人员参考的结构化回复建议。

要求：
防幻觉规则：
{GROUNDING_RULES}

1. 你只能基于 ticket_detail 中的工单标题、描述、状态、优先级、历史回复生成建议。
2. 不能编造 ticket_detail 中不存在的信息。
3. 不能假设系统日志、监控数据、错误码、根因、处理结果、用户反馈、SLA 截止时间。
4. 如果信息不足，请在 risk_flags 中加入“信息不足”，并在 suggestion 中说明需要补充哪些信息。
5. SLA 判断只能使用 ticket_detail JSON 中的 responseDueAt、resolveDueAt、closedAt、slaStatus、slaOverdue、slaRemainingMinutes，不能自行计算官方 deadline 或编造剩余时间。
6. 如果 slaStatus=OVERDUE，请在 risk_flags 中加入“该工单已超过 SLA 解决截止时间”；如果 slaStatus=AT_RISK，请加入“该工单接近 SLA 解决截止时间”；如果 slaStatus=ON_TRACK，不要制造 SLA 风险。
7. 如果缺少 SLA 字段，只能说明“系统未设置 SLA 截止时间”，不要给出精确超时判断。
8. 必须输出合法 JSON，且只能输出 JSON；不要输出 Markdown，不要输出解释文字，不要使用 ```json 代码块。
9. suggestion 语气专业、礼貌、清晰，适合客服复制后稍作修改发给用户。
10. 不要承诺无法确定的处理结果。
11. 只生成回复建议，不执行创建、修改、删除或保存操作。
12. confidence 必须是 0.0 到 1.0 之间的小数。
13. risk_flags 建议从以下值中选择：信息不足、需要人工确认、涉及权限问题、涉及敏感信息、可能需要升级处理、SLA风险、该工单已超过 SLA 解决截止时间、该工单接近 SLA 解决截止时间。

工单信息：
- 标题：{title}
- 内容：{content}
- 状态：{status}
- 优先级：{priority}
- 分类：{category}

历史回复：
{replies_text}

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


def _format_replies(replies: list[Any]) -> str:
    reply_lines = []
    for index, reply_value in enumerate(replies, start=1):
        reply = _safe_dict(reply_value)
        reply_type = _text(
            reply.get("type")
            or reply.get("replyType")
            or reply.get("reply_type")
            or "UNKNOWN"
        )
        content = _text(reply.get("content"))
        created_at = _text(reply.get("createdAt") or reply.get("created_at"))
        reply_lines.append(f"{index}. [{reply_type}] {created_at} {content}".strip())

    return "\n".join(reply_lines) if reply_lines else "暂无历史回复"


def _safe_dict(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    return {key: item for key, item in value.items() if str(key).lower() != "password"}


def _safe_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: _safe_value(item)
            for key, item in value.items()
            if str(key).lower() != "password"
        }
    if isinstance(value, list):
        return [_safe_value(item) for item in value]
    return value


def _text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()
