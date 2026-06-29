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


@router.post("/agent/chat", response_model=AgentChatResponse, response_model_exclude_none=True)
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


def build_agent_chat_response_from_agent_response(
    response: AgentResponse,
) -> AgentChatResponse:
    return AgentChatResponse(
        answer=response.message,
        type=response.type,
        message=response.message,
        data=response.data,
        risk_flags=response.risk_flags,
        intent=response.intent,
        actionType=response.actionType,
        riskLevel=response.riskLevel,
        toolName=response.toolName,
        targetType=response.targetType,
        targetId=response.targetId,
        requiresConfirmation=response.requiresConfirmation,
        error=response.error,
    )
