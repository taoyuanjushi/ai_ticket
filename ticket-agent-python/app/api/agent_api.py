import json
from typing import Any

from fastapi import APIRouter

from app.core.config import settings
from app.core.exceptions import AppException
from app.core.logger import get_logger
from app.schemas.agent_response import AgentResponse
from app.schemas.agent_schema import AgentChatRequest, AgentChatResponse
from app.schemas.tool_schema import ToolInfo, ToolListResponse
from app.services.agent_state_service import AgentStateService
from app.services.agent_tool_service import AgentToolService
from app.services.llm_service import LLMService
from app.services.response_builder import ResponseBuilder
from app.tools.tool_registry import get_all_tools

router = APIRouter()
logger = get_logger(__name__)
llm_service = LLMService()
# Legacy in-memory state is kept for local demos/tests. Real write confirmation
# state is created, confirmed, and cancelled through Java pending_action APIs.
agent_state_service = AgentStateService()
agent_tool_service = AgentToolService(
    llm_service=llm_service,
    state_service=agent_state_service,
)


@router.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": settings.app_name,
    }


@router.post("/agent/chat", response_model=AgentChatResponse)
async def chat(request: AgentChatRequest) -> AgentChatResponse:
    logger.info(
        "Received agent chat request user_id=%s conversation_id=%s",
        request.user_id,
        request.conversation_id,
    )
    try:
        response = await agent_tool_service.handle(request)
    except AppException:
        raise
    except Exception:
        logger.exception("AgentToolService unexpected failure")
        response = ResponseBuilder.error(
            "AI 服务暂时异常，请稍后重试。",
            risk_flags=["Agent服务异常"],
        )
    return build_agent_chat_response_from_agent_response(response)


@router.get("/agent/tools", response_model=ToolListResponse)
async def list_tools() -> ToolListResponse:
    tools = [
        ToolInfo(name=tool.name, description=tool.description or "")
        for tool in get_all_tools()
    ]
    return ToolListResponse(tools=tools)


def build_agent_chat_response(answer: str) -> AgentChatResponse:
    parsed = parse_json_object(answer)
    if parsed is not None:
        structured_type = read_string(parsed.get("type"))
        structured_message = read_string(parsed.get("message"))
        if structured_type and structured_message:
            return AgentChatResponse(
                answer=structured_message,
                type=structured_type,
                message=structured_message,
                data=parsed.get("data"),
                risk_flags=to_string_list(parsed.get("risk_flags")),
            )

        if is_known_ai_json(parsed):
            return AgentChatResponse(
                answer=answer,
                type="JSON_RESULT",
                message="AI 结果已生成。",
                data=parsed,
                risk_flags=to_string_list(parsed.get("risk_flags")),
            )

    response_type, data, risk_flags = classify_text_answer(answer)
    return AgentChatResponse(
        answer=answer,
        type=response_type,
        message=answer,
        data=data,
        risk_flags=risk_flags,
    )


def classify_text_answer(answer: str) -> tuple[str, Any | None, list[str]]:
    if answer == "我还不能确定你的操作意图，请说明是查询、创建、修改状态，还是生成回复建议。":
        return "UNKNOWN_INTENT", None, []

    create_missing_fields = parse_create_missing_fields(answer)
    if create_missing_fields:
        return "MISSING_FIELDS", {"missing_fields": create_missing_fields}, []

    if answer == "请补充工单标题、描述和优先级。":
        return "MISSING_FIELDS", {"missing_fields": ["title", "description", "priority"]}, []

    if answer == "请补充工单标题和描述。":
        return "MISSING_FIELDS", {"missing_fields": ["title", "description"]}, []

    if answer == "创建工单还缺少必要信息：优先级。请说明是 LOW、MEDIUM、HIGH 还是 URGENT，或者用中文说明低/中/高/紧急。":
        return "MISSING_FIELDS", {"missing_fields": ["priority"]}, []

    if answer == "请说明要修改的工单 ID 和目标状态。":
        return "MISSING_FIELDS", {"missing_fields": ["ticket_id", "target_status"]}, []

    if answer == "请说明要修改的工单 ID。":
        return "MISSING_FIELDS", {"missing_fields": ["ticket_id"]}, []

    if answer == "修改工单状态还缺少必要信息：工单 ID。请说明要修改几号工单。":
        return "MISSING_FIELDS", {"missing_fields": ["ticket_id"]}, []

    if answer == "请说明目标状态，可选 PROCESSING 或 CLOSED。":
        return "MISSING_FIELDS", {"missing_fields": ["target_status"]}, []

    if answer.startswith("修改工单状态还缺少必要信息：目标状态。"):
        return "MISSING_FIELDS", {"missing_fields": ["target_status"]}, []

    if answer == "请提供要生成回复建议的工单 ID。":
        return "MISSING_FIELDS", {"missing_fields": ["ticket_id"]}, []

    if answer == "请提供要分析的工单 ID。":
        return "MISSING_FIELDS", {"missing_fields": ["ticket_id"]}, []

    if contains_any(answer, ("登录状态已失效", "请重新登录", "Token格式错误", "token invalid", "token expired")):
        return "UNAUTHORIZED", None, []

    if contains_any(answer, ("没有权限", "无权访问", "你没有权限执行该操作", "目标工单不存在，或你无权访问该工单")):
        return "FORBIDDEN", None, []

    lowered = answer.lower()
    if (
        "请确认" in answer
        or "是否确认" in answer
        or "待确认" in answer
        or "pending" in lowered
        or "pending_action" in lowered
        or "requires_confirmation" in lowered
        or "confirm_required" in lowered
    ):
        return "PENDING_CONFIRMATION", None, []

    if contains_any(answer, ("服务暂时异常", "无法连接", "请稍后重试", "失败")):
        risk_flags = ["LLM调用失败"] if "AI" in answer and "失败" in answer else []
        return "ERROR", None, risk_flags

    return "NORMAL", None, []


def parse_json_object(value: str) -> dict[str, Any] | None:
    try:
        parsed = json.loads(value)
    except (TypeError, ValueError):
        return None
    return parsed if isinstance(parsed, dict) else None


def is_known_ai_json(payload: dict[str, Any]) -> bool:
    return any(
        key in payload
        for key in (
            "suggestion",
            "summary",
            "suggested_priority",
            "suggested_category",
            "similar_tickets",
            "sla_risk_level",
        )
    )


def read_string(value: Any) -> str | None:
    if isinstance(value, str) and value.strip():
        return value.strip()
    return None


def to_string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [item.strip() for item in value if isinstance(item, str) and item.strip()]


def contains_any(text: str, fragments: tuple[str, ...]) -> bool:
    normalized = text.lower()
    return any(fragment in text or fragment.lower() in normalized for fragment in fragments)


def parse_create_missing_fields(answer: str) -> list[str]:
    if not (
        answer.startswith("请补充工单")
        or answer.startswith("创建工单还缺少必要信息：")
    ):
        return []
    field_markers = (
        ("title", "标题"),
        ("description", "描述"),
        ("priority", "优先级"),
    )
    return [field for field, marker in field_markers if marker in answer]


def build_agent_chat_response_from_agent_response(
    response: AgentResponse,
) -> AgentChatResponse:
    return AgentChatResponse(
        answer=response.message,
        type=response.type,
        message=response.message,
        data=response.data,
        risk_flags=response.risk_flags,
    )
