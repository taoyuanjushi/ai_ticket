from app.schemas.intent_schema import IntentType
from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import ReplySuggestionResult
from app.services.grounding import TicketGroundingService


def test_grounding_blocks_log_claim_without_ticket_basis() -> None:
    service = TicketGroundingService()
    result = service.apply_grounding(
        ReplySuggestionResult(
            suggestion="日志显示用户账号异常，请通知用户重试。",
            confidence=0.9,
            reason="日志显示请求失败。",
            risk_flags=[],
        ),
        ticket_detail=detail_without_logs_or_result(),
        task_name="回复建议",
    )

    assert "日志显示" not in result.suggestion
    assert "日志显示" not in result.reason
    assert "信息不足" in result.risk_flags
    assert result.confidence <= 0.6


def test_grounding_blocks_fixed_claim_without_ticket_basis() -> None:
    service = TicketGroundingService()
    result = service.apply_grounding(
        ReplySuggestionResult(
            suggestion="问题已经修复，请用户刷新页面。",
            confidence=0.95,
            reason="模型判断已经修复。",
            risk_flags=[],
        ),
        ticket_detail=detail_without_logs_or_result(),
        task_name="工单摘要",
    )

    assert "已经修复" not in result.suggestion
    assert "已经修复" not in result.reason
    assert "信息不足" in result.risk_flags


def test_grounding_returns_standard_response_when_ticket_detail_is_insufficient() -> None:
    service = TicketGroundingService()
    result = service.apply_grounding(
        ReplySuggestionResult(
            suggestion="建议直接关闭工单。",
            confidence=0.8,
            reason="信息较少。",
            risk_flags=[],
        ),
        ticket_detail=insufficient_detail(),
        task_name="SLA风险提醒",
    )

    assert result.suggestion.startswith("当前工单信息不足，需要补充")
    assert "信息不足" in result.risk_flags
    assert "需要人工确认" in result.risk_flags


def test_grounding_marks_ticket_detail_required_intents() -> None:
    service = TicketGroundingService()

    for intent in (
        IntentType.REPLY_SUGGESTION,
        IntentType.TICKET_SUMMARY,
        IntentType.PRIORITY_SUGGESTION,
        IntentType.CATEGORY_SUGGESTION,
        IntentType.SIMILAR_TICKET_SEARCH,
        IntentType.SLA_RISK_CHECK,
    ):
        assert service.requires_ticket_detail(intent)


def detail_without_logs_or_result() -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 1,
                "title": "登录失败",
                "content": "用户输入正确密码后仍提示错误，无法进入系统。",
                "status": "OPEN",
                "priority": "HIGH",
            },
            "replies": [
                {
                    "id": 1,
                    "ticketId": 1,
                    "content": "请用户补充错误截图和发生时间。",
                    "replyType": "STAFF",
                }
            ],
        }
    )


def insufficient_detail() -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 2,
                "title": "异常",
                "content": "",
                "status": "OPEN",
                "priority": "MEDIUM",
            },
            "replies": [],
        }
    )
