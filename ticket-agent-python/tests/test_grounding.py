from app.schemas.intent_schema import IntentType
from app.schemas.ticket import TicketDetailDTO
from app.schemas.ticket_ai_schema import ReplySuggestionResult
from app.services.grounding import (
    JAVA_CONNECTION_FAILED_FLAG,
    UNSUPPORTED_CONCLUSION_FLAG,
    TicketGroundingService,
)


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
    assert UNSUPPORTED_CONCLUSION_FLAG in result.risk_flags
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
    assert UNSUPPORTED_CONCLUSION_FLAG in result.risk_flags


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


def test_grounding_allows_high_risk_phrase_when_ticket_detail_has_basis() -> None:
    service = TicketGroundingService()
    detail = TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 3,
                "title": "接口报错",
                "content": "日志显示登录接口返回 500，监控显示错误率升高。",
                "status": "OPEN",
                "priority": "HIGH",
            },
            "replies": [],
        }
    )

    result = service.apply_grounding(
        ReplySuggestionResult(
            suggestion="日志显示登录接口异常，请继续排查。",
            confidence=0.8,
            reason="日志显示登录接口返回 500。",
            risk_flags=[],
        ),
        ticket_detail=detail,
    )

    assert result.suggestion.startswith("日志显示")
    assert UNSUPPORTED_CONCLUSION_FLAG not in result.risk_flags
    assert "信息不足" not in result.risk_flags


def test_grounding_marks_sla_timeout_claim_without_deadline_basis() -> None:
    service = TicketGroundingService()
    result = service.apply_grounding(
        ReplySuggestionResult(
            suggestion="还有 2 小时超时，请立即处理。",
            confidence=0.9,
            reason="SLA 已超时风险较高。",
            risk_flags=[],
        ),
        ticket_detail=detail_without_logs_or_result(),
        task_name="SLA风险提醒",
    )

    assert "还有 2 小时超时" not in result.suggestion
    assert "SLA 已超时" not in result.reason
    assert UNSUPPORTED_CONCLUSION_FLAG in result.risk_flags


def test_build_ticket_context_contains_only_allowed_ticket_fields() -> None:
    service = TicketGroundingService()
    detail = TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 4,
                "title": "登录失败",
                "content": "用户无法登录",
                "status": "OPEN",
                "priority": "HIGH",
                "token": "secret-token",
            },
            "user": {"id": 7, "username": "tom", "password": "secret"},
            "replies": [
                {
                    "id": 9,
                    "ticketId": 4,
                    "content": "请补充截图。",
                    "replyType": "STAFF",
                    "userId": 2,
                    "createdAt": "2026-06-16 10:00:00",
                }
            ],
        }
    )

    context = service.build_ticket_context(detail)

    assert context["id"] == 4
    assert context["description"] == "用户无法登录"
    assert context["replies"] == [
        {"content": "请补充截图。", "type": "STAFF", "createdAt": "2026-06-16 10:00:00"}
    ]
    assert "user" not in context
    assert "token" not in str(context).lower()
    assert "password" not in str(context).lower()


def test_get_ticket_grounding_returns_error_response_for_java_failure() -> None:
    service = TicketGroundingService()

    class FailingJavaClient:
        def get_ticket_detail(self, auth_token: str | None, ticket_id: int):
            from app.clients.java_ticket_client import JavaApiError

            raise JavaApiError(
                502,
                "无法连接 Java 后端服务，请确认 Java 后端已启动。",
            )

    result = service.get_ticket_grounding(
        ticket_id=1,
        auth_token="java-token",
        java_ticket_client=FailingJavaClient(),
    )

    assert result.ok is False
    assert result.error_response == {
        "type": "ERROR",
        "message": "无法连接 Java 后端服务，请确认 Java 后端已启动。",
        "data": None,
        "risk_flags": [JAVA_CONNECTION_FAILED_FLAG],
    }


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
