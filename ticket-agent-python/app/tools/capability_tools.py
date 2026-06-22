from typing import Any

from langchain_core.tools import tool

CAPABILITY_TOOL_DESCRIPTION = (
    "Get the current capabilities of the ticket agent. Use this tool when the user "
    "asks what the agent can do, what functions are available, or how the agent can "
    "help. Do not use this tool to query, create, update, or delete tickets."
)

CAPABILITY_TOOL_RESULT: dict[str, Any] = {
    "capabilities": [
        "查询工单：后续阶段支持按状态、优先级、关键词查询工单",
        "创建工单：后续阶段支持根据用户描述创建新工单",
        "修改工单状态：后续阶段支持修改工单处理状态",
        "总结工单：后续阶段支持总结工单列表和处理情况",
        "生成处理建议：后续阶段支持结合知识库为工单生成处理建议",
    ],
    "current_stage": "LangChain Tool 基础阶段",
    "note": "当前阶段只实现能力说明工具，还没有真正连接工单数据。",
}


@tool("get_agent_capabilities", description=CAPABILITY_TOOL_DESCRIPTION)
def get_agent_capabilities() -> dict[str, Any]:
    return CAPABILITY_TOOL_RESULT.copy()
