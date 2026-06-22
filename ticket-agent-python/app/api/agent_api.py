from fastapi import APIRouter

from app.core.config import settings
from app.core.logger import get_logger
from app.schemas.agent_schema import AgentChatRequest, AgentChatResponse
from app.schemas.tool_schema import ToolInfo, ToolListResponse
from app.services.agent_state_service import AgentStateService
from app.services.agent_tool_service import AgentToolService
from app.services.llm_service import LLMService
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
    answer = await agent_tool_service.chat_with_tools(
        request.message,
        auth_token=request.auth_token,
        user_id=request.user_id,
        conversation_id=request.conversation_id,
    )
    return AgentChatResponse(answer=answer)


@router.get("/agent/tools", response_model=ToolListResponse)
async def list_tools() -> ToolListResponse:
    tools = [
        ToolInfo(name=tool.name, description=tool.description or "")
        for tool in get_all_tools()
    ]
    return ToolListResponse(tools=tools)
