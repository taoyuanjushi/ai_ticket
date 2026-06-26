import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.api import agent_api
from app.clients.java_ticket_client import JavaApiError
from app.main import app
from app.schemas.ai_outputs import (
    CategorySuggestionData,
    PrioritySuggestionData,
    ReplySuggestionData,
    SimilarTicketsData,
    SlaRiskData,
    TicketSummaryData,
)
from app.services.llm_json_parser import LLMJsonParseError, parse_llm_json
from app.services.response_builder import ResponseBuilder
from app.services.ticket_ai_service import TicketAiService

client = TestClient(app)


@pytest.mark.parametrize(
    ("message", "expected_message", "expected_data_keys"),
    [
        ("帮我给 1 号工单生成回复建议", "回复建议生成完成", {"suggestion", "confidence", "reason", "risk_flags"}),
        ("总结 1 号工单", "工单摘要生成完成", {"summary", "key_points", "risk_flags"}),
        ("判断 1 号工单优先级是否合理", "优先级建议生成完成", {"suggested_priority", "confidence", "reason", "risk_flags"}),
        ("1 号工单属于什么分类", "分类建议生成完成", {"suggested_category", "confidence", "reason", "risk_flags"}),
        ("1 号工单有没有类似工单", "相似工单检索完成", {"similar_tickets", "risk_flags"}),
        ("1 号工单有 SLA 风险吗", "SLA 风险分析完成", {"sla_risk_level", "reason", "missing_fields", "risk_flags"}),
    ],
)
def test_agent_chat_ai_capabilities_use_agent_response_contract(
    message: str,
    expected_message: str,
    expected_data_keys: set[str],
) -> None:
    response = client.post(
        "/agent/chat",
        json={"message": message, "auth_token": "java-token"},
    )

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"answer", "type", "message", "data", "risk_flags"}
    assert body["type"] == "JSON_RESULT"
    assert body["message"] == expected_message
    assert body["answer"] == expected_message
    assert isinstance(body["data"], dict)
    assert set(body["data"].keys()) == expected_data_keys
    assert isinstance(body["risk_flags"], list)


def test_response_builder_merges_outer_and_data_risk_flags() -> None:
    response = ResponseBuilder.json_result(
        "SLA 风险分析完成",
        SlaRiskData(
            sla_risk_level="UNKNOWN",
            reason="缺少精确 SLA 字段，不能编造截止时间。",
            missing_fields=["resolveDueAt"],
            risk_flags=["SLA字段不足"],
        ),
        risk_flags=["需要人工确认", "SLA字段不足"],
    )

    assert response.type == "JSON_RESULT"
    assert response.risk_flags == ["需要人工确认", "SLA字段不足"]
    assert response.data["risk_flags"] == ["SLA字段不足"]


def test_ai_output_schema_accepts_valid_payloads() -> None:
    ReplySuggestionData(
        suggestion="请用户补充错误截图。",
        confidence=0.8,
        reason="工单信息不足。",
        risk_flags=["信息不足"],
    )
    TicketSummaryData(summary="登录失败工单摘要。", key_points=["状态：OPEN"])
    PrioritySuggestionData(
        suggested_priority="high",
        confidence=0.7,
        reason="包含登录失败关键词。",
    )
    CategorySuggestionData(
        suggested_category="账号登录",
        confidence=0.8,
        reason="标题包含登录。",
    )
    SimilarTicketsData(similar_tickets=[])
    SlaRiskData(
        sla_risk_level="unknown",
        reason="缺少 SLA 字段。",
        missing_fields=["resolveDueAt"],
        risk_flags=["SLA字段不足"],
    )


@pytest.mark.parametrize(
    "factory",
    [
        lambda: ReplySuggestionData(suggestion="x", confidence=-0.1, reason="x"),
        lambda: ReplySuggestionData(suggestion="x", confidence=1.1, reason="x"),
        lambda: PrioritySuggestionData(suggested_priority="高", confidence=0.5, reason="x"),
        lambda: SlaRiskData(sla_risk_level="CRITICAL", reason="x"),
        lambda: SimilarTicketsData(similar_tickets="not-a-list"),
        lambda: TicketSummaryData(summary="x", key_points="not-a-list"),
    ],
)
def test_ai_output_schema_rejects_invalid_payloads(factory) -> None:
    with pytest.raises(ValidationError):
        factory()


@pytest.mark.parametrize(
    ("raw_text", "expected"),
    [
        ('{"suggestion":"ok","confidence":0.8,"reason":"r","risk_flags":[]}', "ok"),
        ('```json\n{"suggestion":"ok","confidence":0.8,"reason":"r","risk_flags":[]}\n```', "ok"),
        ('这是结果：{"suggestion":"ok","confidence":0.8,"reason":"r","risk_flags":[]} 请参考', "ok"),
    ],
)
def test_parse_llm_json_supports_json_markdown_and_explanatory_text(
    raw_text: str,
    expected: str,
) -> None:
    assert parse_llm_json(raw_text)["suggestion"] == expected


def test_parse_llm_json_raises_clear_error_without_stack_for_invalid_json() -> None:
    with pytest.raises(LLMJsonParseError) as exc_info:
        parse_llm_json("这不是 JSON，也没有可提取的对象")

    assert "Traceback" not in str(exc_info.value)


class CountingLLMClient:
    def __init__(self) -> None:
        self.calls = 0

    def generate_text(self, prompt: str) -> str:
        self.calls += 1
        return '{"suggestion":"不应该生成","confidence":0.8,"reason":"不应该调用 LLM","risk_flags":[]}'


class FailingJavaTicketClient:
    def __init__(self, status_code: int, message: str) -> None:
        self.status_code = status_code
        self.message = message

    def get_ticket_detail(self, auth_token: str | None, ticket_id: int):
        raise JavaApiError(self.status_code, self.message)


@pytest.mark.parametrize(
    ("status_code", "message", "expected_type", "expected_risk_flags"),
    [
        (401, "登录状态已失效，请重新登录。", "UNAUTHORIZED", []),
        (403, "你没有权限执行该操作。", "FORBIDDEN", []),
        (404, "目标工单不存在，或你无权访问该工单。", "FORBIDDEN", []),
        (500, "服务暂时异常，请稍后重试。", "ERROR", ["Java服务异常"]),
        (502, "无法连接 Java 后端服务，请确认 Java 后端已启动。", "ERROR", ["Java连接失败"]),
        (504, "Java 后端响应超时，请稍后重试。", "ERROR", ["Java响应超时"]),
    ],
)
def test_java_error_short_circuits_llm_and_returns_agent_response(
    monkeypatch: pytest.MonkeyPatch,
    status_code: int,
    message: str,
    expected_type: str,
    expected_risk_flags: list[str],
) -> None:
    llm_client = CountingLLMClient()
    service = TicketAiService(
        llm_client=llm_client,  # type: ignore[arg-type]
        java_ticket_client=FailingJavaTicketClient(status_code, message),  # type: ignore[arg-type]
    )
    monkeypatch.setattr(agent_api.agent_tool_service, "ticket_ai_service", service)

    response = client.post(
        "/agent/chat",
        json={"message": "帮我给 1 号工单生成回复建议", "auth_token": "java-token"},
    )

    assert response.status_code == 200
    assert response.json()["type"] == expected_type
    assert response.json()["answer"] == message
    assert response.json()["risk_flags"] == expected_risk_flags
    assert llm_client.calls == 0
