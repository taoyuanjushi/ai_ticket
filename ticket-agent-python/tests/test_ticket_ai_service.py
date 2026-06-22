import json

import pytest
from fastapi.testclient import TestClient

from app.clients.java_ticket_client import JavaApiError
from app.clients.java_ticket_client import JavaTicketClient
from app.clients.llm_client import LLMClient, LLMError, MOCK_REPLY_SUGGESTION
from app.core.config import Settings
from app.core.exceptions import AppException, JAVA_API_ERROR, LLM_CALL_FAILED
from app.main import app
from app.prompts.reply_suggestion_prompt import build_reply_suggestion_prompt
from app.schemas.ticket import TicketDTO, TicketDetailDTO
from app.services.ticket_ai_service import REPLY_SUGGESTION_FAILED_MESSAGE
from app.services.ticket_ai_service import TicketAiService
from tests.conftest import FakeJavaTicketClient

client = TestClient(app)


def test_reply_suggestion_api_returns_mock_llm_suggestion() -> None:
    response = client.post(
        "/ai/tickets/1/reply-suggestion",
        json={"auth_token": "java-token"},
    )

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"suggestion", "confidence", "reason", "risk_flags"}
    assert body["suggestion"] == MOCK_REPLY_SUGGESTION
    assert 0.0 <= body["confidence"] <= 1.0
    assert body["reason"]
    assert body["risk_flags"] == ["需要人工确认"]
    assert "java-token" in FakeJavaTicketClient.seen_tokens


def test_reply_suggestion_api_maps_ticket_not_found() -> None:
    response = client.post(
        "/ai/tickets/999/reply-suggestion",
        json={"auth_token": "java-token"},
    )

    assert response.status_code == 404
    body = response.json()
    assert body["code"] == "JAVA_API_ERROR"
    assert body["message"] == "目标工单不存在，或你无权访问该工单。"


def test_prompt_builder_uses_ticket_detail_and_ignores_password() -> None:
    prompt = build_reply_suggestion_prompt(
        {
            "ticket": {
                "title": "登录失败",
                "content": "用户输入正确密码后仍提示错误",
                "status": "OPEN",
                "priority": "HIGH",
                "category": "ACCOUNT",
                "password": "secret",
            },
            "user": {"username": "tom", "password": "secret"},
            "replies": [
                {
                    "replyType": "STAFF",
                    "content": "请提供错误截图",
                    "createdAt": "2026-06-16",
                }
            ],
        }
    )

    assert "登录失败" in prompt
    assert "用户输入正确密码后仍提示错误" in prompt
    assert "请提供错误截图" in prompt
    assert "secret" not in prompt
    assert "只生成回复建议" in prompt
    assert "必须输出合法 JSON" in prompt
    assert "你只能使用 ticket_detail 中的信息。" in prompt
    assert "不能假设系统日志、报错码、处理结果" in prompt
    assert "不能编造 ticket_detail 中不存在的信息" in prompt
    assert '"suggestion": "..."' in prompt


def test_llm_client_mock_mode_returns_local_suggestion() -> None:
    app_settings = Settings(llm_mock_mode=True)
    llm_client = LLMClient(app_settings=app_settings)

    body = json.loads(llm_client.generate_text("prompt"))
    assert body["suggestion"] == MOCK_REPLY_SUGGESTION
    assert body["confidence"] == 0.8
    assert body["risk_flags"] == ["需要人工确认"]


def test_llm_client_without_config_when_mock_disabled_raises_clear_error() -> None:
    app_settings = Settings(
        llm_mock_mode=False,
        llm_api_key="",
        llm_api_base_url="",
        llm_base_url="",
        llm_model="",
    )
    llm_client = LLMClient(app_settings=app_settings)

    with pytest.raises(LLMError, match="LLM 配置不完整"):
        llm_client.generate_text("prompt")


def test_ticket_ai_service_maps_llm_error() -> None:
    class FailingLLMClient:
        def generate_text(self, prompt: str) -> str:
            raise LLMError("LLM 服务调用失败：HTTP 500")

    service = TicketAiService(llm_client=FailingLLMClient())  # type: ignore[arg-type]

    with pytest.raises(AppException) as exc_info:
        service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert exc_info.value.code == LLM_CALL_FAILED
    assert "生成 AI 回复建议失败" in exc_info.value.message


def test_ticket_ai_service_repairs_invalid_json_once() -> None:
    content = """
这是建议：
```json
{
  "suggestion": "请用户补充错误截图。",
  "confidence": 0.72,
  "reason": "历史回复已经要求补充截图。",
  "risk_flags": ["信息不足"]
}
```
""".strip()
    service = TicketAiService(
        llm_client=StaticLLMClient(content),  # type: ignore[arg-type]
        java_ticket_client=StaticJavaTicketClient(normal_ticket_detail()),  # type: ignore[arg-type]
    )

    result = service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert result.suggestion == "请用户补充错误截图。"
    assert result.confidence == 0.72
    assert result.risk_flags == ["信息不足"]


def test_ticket_ai_service_returns_readable_error_when_json_repair_fails() -> None:
    service = TicketAiService(
        llm_client=StaticLLMClient("这不是 JSON"),  # type: ignore[arg-type]
        java_ticket_client=StaticJavaTicketClient(normal_ticket_detail()),  # type: ignore[arg-type]
    )

    with pytest.raises(AppException) as exc_info:
        service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert exc_info.value.code == LLM_CALL_FAILED
    assert exc_info.value.message == REPLY_SUGGESTION_FAILED_MESSAGE


def test_ticket_ai_service_marks_insufficient_information_without_fabricating() -> None:
    content = json.dumps(
        {
            "suggestion": "建议告知用户问题已解决。",
            "confidence": 0.9,
            "reason": "基于工单信息生成。",
            "risk_flags": [],
        },
        ensure_ascii=False,
    )
    service = TicketAiService(
        llm_client=StaticLLMClient(content),  # type: ignore[arg-type]
        java_ticket_client=StaticJavaTicketClient(insufficient_ticket_detail()),  # type: ignore[arg-type]
    )

    result = service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert "信息不足" in result.risk_flags
    assert result.confidence <= 0.6
    assert "补充" in result.suggestion
    assert "已解决" not in result.suggestion


def test_ticket_ai_service_does_not_call_llm_when_java_denies_access() -> None:
    llm_client = StaticLLMClient(
        json.dumps(
            {
                "suggestion": "不应该生成",
                "confidence": 0.8,
                "reason": "不应该调用 LLM",
                "risk_flags": [],
            },
            ensure_ascii=False,
        )
    )
    service = TicketAiService(
        llm_client=llm_client,  # type: ignore[arg-type]
        java_ticket_client=ForbiddenJavaTicketClient(),  # type: ignore[arg-type]
    )

    with pytest.raises(AppException) as exc_info:
        service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert exc_info.value.code == JAVA_API_ERROR
    assert exc_info.value.message == "你没有权限执行该操作。"
    assert llm_client.calls == 0


def test_ticket_ai_service_does_not_call_llm_when_ticket_not_found() -> None:
    llm_client = StaticLLMClient(
        json.dumps(
            {
                "suggestion": "不应该生成",
                "confidence": 0.8,
                "reason": "不应该调用 LLM",
                "risk_flags": [],
            },
            ensure_ascii=False,
        )
    )
    service = TicketAiService(
        llm_client=llm_client,  # type: ignore[arg-type]
        java_ticket_client=NotFoundJavaTicketClient(),  # type: ignore[arg-type]
    )

    with pytest.raises(AppException) as exc_info:
        service.generate_reply_suggestion_by_ticket_id(999, auth_token="java-token")

    assert exc_info.value.code == JAVA_API_ERROR
    assert exc_info.value.message == "目标工单不存在，或你无权访问该工单。"
    assert llm_client.calls == 0


def test_reply_suggestion_reuses_grounding_for_unsupported_log_claim() -> None:
    llm_client = StaticLLMClient(
        json.dumps(
            {
                "suggestion": "日志显示用户密码错误，请用户重新登录。",
                "confidence": 0.91,
                "reason": "日志显示登录失败。",
                "risk_flags": [],
            },
            ensure_ascii=False,
        )
    )
    service = TicketAiService(
        llm_client=llm_client,  # type: ignore[arg-type]
        java_ticket_client=StaticJavaTicketClient(normal_ticket_detail()),  # type: ignore[arg-type]
    )

    result = service.generate_reply_suggestion_by_ticket_id(1, auth_token="java-token")

    assert llm_client.calls == 1
    assert "日志显示" not in result.suggestion
    assert "日志显示" not in result.reason
    assert "信息不足" in result.risk_flags


@pytest.mark.parametrize(
    ("method_name", "expected_keys"),
    [
        ("generate_ticket_summary_by_ticket_id", {"summary", "key_points", "risk_flags"}),
        ("suggest_priority_by_ticket_id", {"suggested_priority", "confidence", "reason", "risk_flags"}),
        ("suggest_category_by_ticket_id", {"suggested_category", "confidence", "reason", "risk_flags"}),
        ("search_similar_tickets_by_ticket_id", {"similar_tickets", "risk_flags"}),
        ("check_sla_risk_by_ticket_id", {"sla_risk_level", "reason", "missing_fields", "risk_flags"}),
    ],
)
def test_ticket_ai_service_new_capabilities_return_structured_results(
    method_name: str,
    expected_keys: set[str],
) -> None:
    service = TicketAiService(
        llm_client=StaticLLMClient("{}"),  # type: ignore[arg-type]
        java_ticket_client=StaticJavaTicketClient(normal_ticket_detail()),  # type: ignore[arg-type]
    )

    method = getattr(service, method_name)
    result = method(1, auth_token="java-token")
    payload = result.model_dump(mode="json")
    payload_text = json.dumps(payload, ensure_ascii=False)

    assert set(payload.keys()) == expected_keys
    for phrase in ("日志显示", "已经修复", "监控显示", "已完成处理"):
        assert phrase not in payload_text


def test_java_ticket_client_get_ticket_detail_uses_detail_path(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    java_client = JavaTicketClient()
    called = {}

    def fake_request(
        method: str,
        path: str,
        auth_token: str | None = None,
        params: dict | None = None,
        json: dict | None = None,
    ) -> dict:
        called["method"] = method
        called["path"] = path
        called["auth_token"] = auth_token
        return {
            "ticket": {
                "id": 3,
                "title": "登录失败",
                "content": "用户无法登录",
                "status": "OPEN",
                "priority": "HIGH",
            },
            "replies": [],
        }

    monkeypatch.setattr(java_client, "_request", fake_request)

    result = java_client.get_ticket_detail(3, token="java-token")

    assert result.id == 3
    assert result.title == "登录失败"
    assert called == {
        "method": "GET",
        "path": "/tickets/3/detail",
        "auth_token": "java-token",
    }


class StaticLLMClient:
    def __init__(self, content: str) -> None:
        self.content = content
        self.calls = 0

    def generate_text(self, prompt: str) -> str:
        self.calls += 1
        return self.content


class StaticJavaTicketClient:
    def __init__(self, detail: TicketDetailDTO) -> None:
        self.detail = detail

    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        return self.detail

    def search_tickets(
        self,
        auth_token: str | None = None,
        query: dict | None = None,
    ) -> list[TicketDTO]:
        return [
            TicketDTO.model_validate(
                {
                    "id": 1,
                    "title": "登录失败",
                    "content": "用户输入正确密码后仍提示错误",
                    "status": "OPEN",
                    "priority": "HIGH",
                }
            ),
            TicketDTO.model_validate(
                {
                    "id": 3,
                    "title": "登录接口超时",
                    "content": "用户登录时接口响应超过 10 秒",
                    "status": "PROCESSING",
                    "priority": "HIGH",
                }
            ),
        ]


class ForbiddenJavaTicketClient:
    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        raise JavaApiError(403, "你没有权限执行该操作。")


class NotFoundJavaTicketClient:
    def get_ticket_detail(
        self,
        auth_token: str | None,
        ticket_id: int,
    ) -> TicketDetailDTO:
        raise JavaApiError(404, "目标工单不存在，或你无权访问该工单。")


def normal_ticket_detail() -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 1,
                "title": "登录失败",
                "content": "用户输入正确密码后仍提示错误",
                "status": "OPEN",
                "priority": "HIGH",
            },
            "replies": [
                {
                    "id": 1,
                    "ticketId": 1,
                    "content": "请用户补充错误截图。",
                    "replyType": "STAFF",
                }
            ],
        }
    )


def insufficient_ticket_detail() -> TicketDetailDTO:
    return TicketDetailDTO.from_java_detail(
        {
            "ticket": {
                "id": 1,
                "title": "登录失败",
                "content": "",
                "status": "OPEN",
                "priority": "HIGH",
            },
            "replies": [],
        }
    )
