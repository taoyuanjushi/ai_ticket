import pytest

from app.clients.java_ticket_client import JavaApiError
from app.core.exceptions import AppException, JAVA_API_ERROR
from app.schemas.ticket import TicketDTO, TicketDetailDTO
from app.services.ai_capabilities.category import CategorySuggestionService
from app.services.ai_capabilities.priority import PrioritySuggestionService
from app.services.ai_capabilities.similar import SimilarTicketSearchService
from app.services.ai_capabilities.sla import SlaRiskService
from app.services.ai_capabilities.summary import TicketSummaryService


def test_ticket_summary_capability() -> None:
    result = TicketSummaryService(
        java_ticket_client=StaticJavaClient(normal_ticket_detail())
    ).summarize(auth_token="java-token", ticket_id=1)

    assert "登录失败" in result.summary
    assert "状态：OPEN" in result.key_points
    assert result.risk_flags == []


def test_priority_suggestion_capability() -> None:
    result = PrioritySuggestionService(
        java_ticket_client=StaticJavaClient(normal_ticket_detail())
    ).suggest(auth_token="java-token", ticket_id=1)

    assert result.suggested_priority == "HIGH"
    assert 0.0 <= result.confidence <= 1.0
    assert result.risk_flags == []


def test_category_suggestion_capability() -> None:
    result = CategorySuggestionService(
        java_ticket_client=StaticJavaClient(normal_ticket_detail())
    ).suggest(auth_token="java-token", ticket_id=1)

    assert result.suggested_category == "账号登录"
    assert 0.0 <= result.confidence <= 1.0
    assert result.risk_flags == []


def test_similar_ticket_search_capability() -> None:
    result = SimilarTicketSearchService(
        java_ticket_client=StaticJavaClient(normal_ticket_detail())
    ).search(auth_token="java-token", ticket_id=1)

    assert [ticket.id for ticket in result.similar_tickets] == [3]
    assert result.similar_tickets[0].title == "登录接口超时"
    assert "登录" in result.similar_tickets[0].similarity_reason
    assert result.risk_flags == []


def test_sla_risk_capability_returns_missing_fields_without_exact_time() -> None:
    result = SlaRiskService(
        java_ticket_client=StaticJavaClient(normal_ticket_detail())
    ).check(auth_token="java-token", ticket_id=1)

    assert result.sla_risk_level == "UNKNOWN"
    assert "resolveDueAt" in result.missing_fields
    assert "SLA字段不足" in result.risk_flags
    assert "无法判断精确风险" in result.reason
    assert "2 小时" not in result.reason
    assert "还有" not in result.reason


def test_sla_risk_capability_uses_java_overdue_status() -> None:
    result = SlaRiskService(
        java_ticket_client=StaticJavaClient(sla_ticket_detail("OVERDUE", True, -30))
    ).check(auth_token="java-token", ticket_id=1)

    assert result.sla_risk_level == "HIGH"
    assert result.missing_fields == []
    assert "该工单已超过 SLA 解决截止时间" in result.risk_flags
    assert "slaStatus=OVERDUE" in result.reason


def test_sla_risk_capability_uses_java_at_risk_status() -> None:
    result = SlaRiskService(
        java_ticket_client=StaticJavaClient(sla_ticket_detail("AT_RISK", False, 120))
    ).check(auth_token="java-token", ticket_id=1)

    assert result.sla_risk_level == "MEDIUM"
    assert result.missing_fields == []
    assert "该工单接近 SLA 解决截止时间" in result.risk_flags
    assert "slaStatus=AT_RISK" in result.reason


def test_sla_risk_capability_does_not_invent_risk_when_on_track() -> None:
    result = SlaRiskService(
        java_ticket_client=StaticJavaClient(sla_ticket_detail("ON_TRACK", False, 900))
    ).check(auth_token="java-token", ticket_id=1)

    assert result.sla_risk_level == "LOW"
    assert result.missing_fields == []
    assert "该工单已超过 SLA 解决截止时间" not in result.risk_flags
    assert "该工单接近 SLA 解决截止时间" not in result.risk_flags


@pytest.mark.parametrize(
    ("service", "method_name"),
    [
        (TicketSummaryService, "summarize"),
        (PrioritySuggestionService, "suggest"),
        (CategorySuggestionService, "suggest"),
        (SimilarTicketSearchService, "search"),
        (SlaRiskService, "check"),
    ],
)
def test_ai_capabilities_fail_when_java_denies_access(
    service,
    method_name: str,
) -> None:
    instance = service(java_ticket_client=ForbiddenJavaClient())
    method = getattr(instance, method_name)

    with pytest.raises(AppException) as exc_info:
        method(auth_token="java-token", ticket_id=1)

    assert exc_info.value.code == JAVA_API_ERROR
    assert exc_info.value.status_code == 403
    assert exc_info.value.message == "你没有权限执行该操作。"


def test_ai_capabilities_mark_insufficient_information() -> None:
    detail = insufficient_ticket_detail()
    java_client = StaticJavaClient(detail)

    summary = TicketSummaryService(java_ticket_client=java_client).summarize("java-token", 1)
    priority = PrioritySuggestionService(java_ticket_client=java_client).suggest("java-token", 1)
    category = CategorySuggestionService(java_ticket_client=java_client).suggest("java-token", 1)
    similar = SimilarTicketSearchService(java_ticket_client=java_client).search("java-token", 1)
    sla = SlaRiskService(java_ticket_client=java_client).check("java-token", 1)

    assert "信息不足" in summary.risk_flags
    assert "信息不足" in priority.risk_flags
    assert "信息不足" in category.risk_flags
    assert "信息不足" in similar.risk_flags
    assert "信息不足" in sla.risk_flags


def test_similar_ticket_search_stops_when_target_detail_not_found() -> None:
    java_client = NotFoundCountingJavaClient()

    with pytest.raises(AppException) as exc_info:
        SimilarTicketSearchService(java_ticket_client=java_client).search(
            auth_token="java-token",
            ticket_id=99,
        )

    assert exc_info.value.status_code == 404
    assert java_client.detail_calls == 1
    assert java_client.search_calls == 0


class StaticJavaClient:
    def __init__(self, detail: TicketDetailDTO) -> None:
        self.detail = detail
        self.seen_tokens: list[str | None] = []

    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        self.seen_tokens.append(auth_token)
        return self.detail

    def search_tickets(
        self,
        auth_token: str | None = None,
        query: dict | None = None,
    ) -> list[TicketDTO]:
        self.seen_tokens.append(auth_token)
        keyword = (query or {}).get("keyword")
        tickets = [
            {
                "id": 1,
                "title": "登录失败",
                "content": "用户输入正确密码后仍提示错误",
                "status": "OPEN",
                "priority": "HIGH",
            },
            {
                "id": 3,
                "title": "登录接口超时",
                "content": "用户登录时接口响应超过 10 秒",
                "status": "PROCESSING",
                "priority": "HIGH",
            },
        ]
        return [
            TicketDTO.model_validate(ticket)
            for ticket in tickets
            if not keyword or keyword in ticket["title"] or keyword in ticket["content"]
        ]


class ForbiddenJavaClient:
    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        raise JavaApiError(403, "你没有权限执行该操作。")


class NotFoundCountingJavaClient:
    def __init__(self) -> None:
        self.detail_calls = 0
        self.search_calls = 0

    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        self.detail_calls += 1
        raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")

    def search_tickets(
        self,
        auth_token: str | None = None,
        query: dict | None = None,
    ) -> list[TicketDTO]:
        self.search_calls += 1
        return [TicketDTO.model_validate({"id": 3, "title": "不应返回", "status": "OPEN"})]


def normal_ticket_detail() -> TicketDetailDTO:
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
                    "content": "请用户补充错误截图。",
                    "replyType": "STAFF",
                    "createdAt": "2026-06-16 10:00:00",
                }
            ],
        }
    )


def sla_ticket_detail(
    sla_status: str,
    sla_overdue: bool,
    sla_remaining_minutes: int,
) -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 1,
                "title": "登录失败",
                "content": "用户输入正确密码后仍提示错误，无法进入系统。",
                "status": "OPEN",
                "priority": "HIGH",
                "responseDueAt": "2026-06-30T12:00:00",
                "resolveDueAt": "2026-07-01T10:00:00",
                "closedAt": None,
                "slaStatus": sla_status,
                "slaOverdue": sla_overdue,
                "slaRemainingMinutes": sla_remaining_minutes,
            },
            "replies": [
                {
                    "id": 1,
                    "ticketId": 1,
                    "content": "请用户补充错误截图。",
                    "replyType": "STAFF",
                    "createdAt": "2026-06-16 10:00:00",
                }
            ],
        }
    )


def insufficient_ticket_detail() -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 1,
                "title": "异常",
                "content": "",
                "status": "OPEN",
                "priority": "MEDIUM",
            },
            "replies": [],
        }
    )
