import json
from typing import Any

from langchain_core.tools import BaseTool

from app.clients.java_ticket_client import JavaApiError, JavaTicketClient
from app.core.exceptions import AppException, TOOL_CALL_FAILED
from app.core.logger import get_logger
from app.schemas.agent_state_schema import PendingAction
from app.schemas.intent_schema import IntentResult, IntentType
from app.schemas.pending_action import (
    AiPendingActionConfirmResult,
    AiPendingActionDTO,
    AiPendingActionType,
    CreateAiPendingActionRequest,
)
from app.services.agent_state_service import AgentStateService
from app.services.intent_recognizer import IntentRecognizer
from app.services.llm_service import LLMService
from app.services.pending_action_store import DEFAULT_SESSION_KEY
from app.services.ticket_ai_service import TicketAiService
from app.tools.capability_tools import get_agent_capabilities
from app.tools.ticket_tools import create_ticket, search_tickets, update_ticket_status

logger = get_logger(__name__)

UNKNOWN_INTENT_ANSWER = (
    "我还不能确定你的操作意图，请说明是查询、创建、修改状态，还是生成回复建议。"
)

CAPABILITY_TRIGGERS = (
    "你能做什么",
    "你有什么功能",
    "你有哪些功能",
    "你可以帮我做什么",
    "有哪些工具",
    "agent 能力",
    "agent能力",
    "功能列表",
)

GROUNDED_TICKET_AI_INTENTS = {
    IntentType.TICKET_SUMMARY,
    IntentType.PRIORITY_SUGGESTION,
    IntentType.CATEGORY_SUGGESTION,
    IntentType.SIMILAR_TICKET_SEARCH,
    IntentType.SLA_RISK_CHECK,
}

GROUNDED_TICKET_AI_LABELS = {
    IntentType.TICKET_SUMMARY: "工单摘要",
    IntentType.PRIORITY_SUGGESTION: "优先级建议",
    IntentType.CATEGORY_SUGGESTION: "分类建议",
    IntentType.SIMILAR_TICKET_SEARCH: "相似工单检索",
    IntentType.SLA_RISK_CHECK: "SLA 风险提醒",
}


class AgentToolService:
    def __init__(
        self,
        llm_service: LLMService | None = None,
        state_service: AgentStateService | None = None,
        intent_recognizer: IntentRecognizer | None = None,
        ticket_ai_service: TicketAiService | None = None,
        java_ticket_client: JavaTicketClient | None = None,
        capability_tool: BaseTool = get_agent_capabilities,
        search_ticket_tool: BaseTool = search_tickets,
        create_ticket_tool: BaseTool = create_ticket,
        update_ticket_status_tool: BaseTool = update_ticket_status,
    ) -> None:
        self.llm_service = llm_service or LLMService()
        # Legacy in-memory state is accepted for old tests/local demos only.
        # Real pending_action state is created and confirmed through Java.
        self.state_service = state_service
        self.intent_recognizer = intent_recognizer or IntentRecognizer()
        self.ticket_ai_service = ticket_ai_service or TicketAiService()
        self.java_ticket_client = java_ticket_client or JavaTicketClient()
        self.capability_tool = capability_tool
        self.search_ticket_tool = search_ticket_tool
        self.create_ticket_tool = create_ticket_tool
        self.update_ticket_status_tool = update_ticket_status_tool

    async def chat_with_tools(
        self,
        message: str,
        auth_token: str | None = None,
        user_id: str | None = None,
        conversation_id: str | None = None,
    ) -> str:
        resolved_conversation_id = self._resolve_conversation_id(
            user_id=user_id,
            conversation_id=conversation_id,
        )
        intent_result = self.intent_recognizer.recognize(message)
        logger.info(
            "Agent intent recognized: intent=%s confidence=%s missing_fields=%s",
            intent_result.intent,
            intent_result.confidence,
            intent_result.missing_fields,
        )

        return self._dispatch_intent(
            message=message,
            intent_result=intent_result,
            auth_token=auth_token,
            conversation_id=resolved_conversation_id,
        )

    def _dispatch_intent(
        self,
        message: str,
        intent_result: IntentResult,
        auth_token: str | None,
        conversation_id: str,
    ) -> str:
        # Confirmation commands are handled before every other branch so a
        # plain "确认" or "取消" cannot be mistaken for a new business request.
        if intent_result.intent == IntentType.CONFIRM:
            return self.handle_pending_action_approval(
                conversation_id=conversation_id,
                auth_token=auth_token,
            )

        if intent_result.intent == IntentType.CANCEL:
            return self.handle_pending_action_cancellation(
                conversation_id=conversation_id,
                auth_token=auth_token,
            )

        # Read-only branches can execute immediately because Java still enforces
        # ticket visibility and permission checks.
        if intent_result.intent == IntentType.QUERY_TICKET:
            return self._handle_query_ticket_intent(
                intent_result=intent_result,
                auth_token=auth_token,
            )

        # Write branches only create Java pending_action records. The real
        # create/update/save happens later when Java confirms the action.
        if intent_result.intent == IntentType.CREATE_TICKET:
            return self._handle_create_ticket_intent(
                intent_result=intent_result,
                conversation_id=conversation_id,
                auth_token=auth_token,
            )

        if intent_result.intent == IntentType.UPDATE_TICKET_STATUS:
            return self._handle_update_ticket_status_intent(
                intent_result=intent_result,
                conversation_id=conversation_id,
                auth_token=auth_token,
            )

        if intent_result.intent == IntentType.REPLY_SUGGESTION:
            return self._handle_reply_suggestion_intent(
                intent_result=intent_result,
                auth_token=auth_token,
            )

        if intent_result.intent == IntentType.SAVE_AI_REPLY:
            return self._handle_save_ai_reply_intent(
                intent_result=intent_result,
                conversation_id=conversation_id,
                auth_token=auth_token,
            )

        if intent_result.intent in GROUNDED_TICKET_AI_INTENTS:
            return self._handle_grounded_ticket_ai_intent(
                intent_result=intent_result,
                auth_token=auth_token,
            )

        if self.should_call_capability_tool(message):
            logger.info("Agent tool called: get_agent_capabilities")
            tool_result = self.call_capability_tool()
            return self._format_capability_answer(tool_result)

        return self._handle_unknown_intent()

    def _handle_query_ticket_intent(
        self,
        intent_result: IntentResult,
        auth_token: str | None,
    ) -> str:
        params = self._build_search_params(intent_result)
        if auth_token:
            params["auth_token"] = auth_token
        tool_result = self.call_search_tickets(params)
        return self.format_search_tickets_answer(tool_result, params)

    def _handle_unknown_intent(self) -> str:
        return UNKNOWN_INTENT_ANSWER

    def should_call_capability_tool(self, message: str) -> bool:
        normalized_message = message.strip().lower()
        return any(trigger.lower() in normalized_message for trigger in CAPABILITY_TRIGGERS)

    def call_capability_tool(self) -> dict[str, Any]:
        try:
            result = self.capability_tool.invoke({})
        except Exception as exc:
            logger.exception("Agent tool call failed: get_agent_capabilities")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工具调用失败，请稍后重试",
                status_code=500,
            ) from exc

        if not isinstance(result, dict):
            logger.error("Agent tool returned unexpected result type")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工具调用失败，请稍后重试",
                status_code=500,
            )

        return result

    def _build_search_params(self, intent_result: IntentResult) -> dict[str, Any]:
        params: dict[str, Any] = {}
        if intent_result.target_status:
            params["status"] = intent_result.target_status
        if intent_result.priority:
            params["priority"] = intent_result.priority
        if intent_result.keyword:
            params["keyword"] = intent_result.keyword
        return params

    def call_search_tickets(self, params: dict[str, Any]) -> dict[str, Any]:
        try:
            logger.info("Agent tool called: search_tickets")
            logger.info("Search tickets params: %s", self._safe_tool_params(params))
            result = self.search_ticket_tool.invoke(params)
        except AppException:
            raise
        except Exception as exc:
            logger.exception("Agent tool call failed: search_tickets")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单查询工具调用失败，请稍后重试",
                status_code=500,
            ) from exc

        if not isinstance(result, dict):
            logger.error("Search tickets tool returned unexpected result type")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单查询工具调用失败，请稍后重试",
                status_code=500,
            )

        logger.info("Search tickets result total: %s", result.get("total"))
        return result

    def _handle_create_ticket_intent(
        self,
        intent_result: IntentResult,
        conversation_id: str,
        auth_token: str | None,
    ) -> str:
        logger.info("Agent intent detected: create_ticket")
        if intent_result.missing_fields:
            if (
                intent_result.missing_fields == ["priority"]
                and self._has_explicit_priority_text(intent_result.raw_message)
            ):
                return self.format_invalid_create_ticket_priority_answer()
            logger.info("Create ticket missing fields: %s", intent_result.missing_fields)
            return self.format_missing_create_ticket_fields_answer(
                intent_result.missing_fields
            )

        params = {
            "title": str(intent_result.title),
            "description": str(intent_result.description),
            "priority": str(intent_result.priority),
        }

        pending_action = self.create_pending_create_ticket_action(params)
        self.create_java_pending_action(
            auth_token=auth_token,
            conversation_id=conversation_id,
            action_type=AiPendingActionType.CREATE_TICKET,
            payload={
                "title": str(intent_result.title),
                "content": str(intent_result.description),
                "priority": str(intent_result.priority),
            },
        )
        logger.info("Java pending action created: create_ticket conversation_id=%s", conversation_id)
        return self.format_create_ticket_confirmation(pending_action)

    def call_create_ticket(self, params: dict[str, str | bool]) -> dict[str, Any]:
        """Legacy direct write helper.

        /agent/chat does not call this method for user create requests. Real
        production writes are executed by Java after pending_action confirmation.
        Keep this only for direct tool tests and local experiments.
        """
        tool_params = {
            "title": str(params["title"]),
            "description": str(params["description"]),
            "priority": str(params["priority"]),
        }
        if params.get("auth_token"):
            tool_params["auth_token"] = str(params["auth_token"])
        try:
            logger.info("Agent tool called: create_ticket")
            logger.info("Create ticket params: %s", self._safe_tool_params(tool_params))
            result = self.create_ticket_tool.invoke(tool_params)
        except AppException:
            raise
        except Exception as exc:
            logger.exception("Agent tool call failed: create_ticket")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单创建工具调用失败，请稍后重试",
                status_code=500,
            ) from exc

        if not isinstance(result, dict):
            logger.error("Create ticket tool returned unexpected result type")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单创建工具调用失败，请稍后重试",
                status_code=500,
            )

        ticket = result.get("ticket", {})
        if isinstance(ticket, dict):
            logger.info("Created Java ticket id: %s", ticket.get("id"))
        return result

    def create_pending_create_ticket_action(
        self, params: dict[str, str | bool]
    ) -> PendingAction:
        action = PendingAction(
            tool_name="create_ticket",
            args={
                "title": str(params["title"]),
                "description": str(params["description"]),
                "priority": str(params["priority"]),
            },
            summary=(f"创建工单：{params['title']}，优先级 {params['priority']}"),
            action_type="write",
        )
        return action

    def _handle_update_ticket_status_intent(
        self,
        intent_result: IntentResult,
        conversation_id: str,
        auth_token: str | None,
    ) -> str:
        logger.info("Agent intent detected: update_ticket_status")
        if intent_result.missing_fields:
            if (
                intent_result.missing_fields == ["target_status"]
                and self._has_explicit_status_update_text(intent_result.raw_message)
            ):
                return self.format_invalid_update_ticket_status_answer()
            logger.info(
                "Update ticket status missing fields: %s",
                intent_result.missing_fields,
            )
            return self.format_missing_update_ticket_status_fields_answer(
                intent_result.missing_fields
            )

        params: dict[str, int | str | bool] = {
            "ticket_id": int(intent_result.ticket_id),
            "status": str(intent_result.target_status),
        }

        pending_action, error_message = self.create_pending_update_ticket_status_action(
            params
        )
        if error_message:
            return error_message
        if pending_action is None:
            return "工单状态修改失败，请检查工单 ID 和目标状态。"

        self.create_java_pending_action(
            auth_token=auth_token,
            conversation_id=conversation_id,
            action_type=AiPendingActionType.UPDATE_TICKET_STATUS,
            payload={
                "ticketId": int(intent_result.ticket_id),
                "status": str(intent_result.target_status),
            },
        )
        logger.info("Java pending action created: update_ticket_status conversation_id=%s", conversation_id)
        return self.format_update_ticket_status_confirmation(pending_action)

    def call_update_ticket_status(
        self, params: dict[str, int | str | bool]
    ) -> dict[str, Any]:
        """Legacy direct write helper.

        /agent/chat stages status changes as Java pending_action records. This
        direct method is retained only for old direct-tool tests.
        """
        tool_params = {
            "ticket_id": int(params["ticket_id"]),
            "status": str(params["status"]),
        }
        if params.get("auth_token"):
            tool_params["auth_token"] = str(params["auth_token"])
        try:
            logger.info("Agent tool called: update_ticket_status")
            logger.info(
                "Update ticket status params: %s",
                self._safe_tool_params(tool_params),
            )
            result = self.update_ticket_status_tool.invoke(tool_params)
        except AppException:
            raise
        except Exception as exc:
            logger.exception("Agent tool call failed: update_ticket_status")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单状态修改工具调用失败，请稍后重试",
                status_code=500,
            ) from exc

        if not isinstance(result, dict):
            logger.error("Update ticket status tool returned unexpected result type")
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单状态修改工具调用失败，请稍后重试",
                status_code=500,
            )

        if result.get("success"):
            logger.info(
                "Updated Java ticket id: %s from %s to %s",
                result.get("id"),
                result.get("old_status"),
                result.get("new_status"),
            )
        return result

    def create_pending_update_ticket_status_action(
        self, params: dict[str, int | str | bool]
    ) -> tuple[PendingAction | None, str | None]:
        ticket_id = int(params["ticket_id"])
        new_status = str(params["status"])

        action = PendingAction(
            tool_name="update_ticket_status",
            args={"ticket_id": ticket_id, "status": new_status},
            summary=f"将 {ticket_id} 号工单状态修改为 {new_status}",
            action_type="write",
        )
        return action, None

    def create_java_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
        action_type: AiPendingActionType,
        payload: dict[str, Any],
    ) -> AiPendingActionDTO:
        try:
            return self.java_ticket_client.create_pending_action(
                auth_token=auth_token,
                req=CreateAiPendingActionRequest(
                    conversationId=conversation_id,
                    actionType=action_type,
                    payload=self._strip_sensitive_payload(payload),
                ),
            )
        except JavaApiError as exc:
            logger.exception("Create Java pending action failed")
            raise AppException(
                code="JAVA_API_ERROR",
                message=exc.message,
                status_code=exc.status_code,
            ) from exc

    def create_pending_save_ai_reply_action(
        self,
        auth_token: str | None,
        conversation_id: str,
        ticket_id: int,
        content: str,
        confidence: float | None = None,
        reason: str | None = None,
        risk_flags: list[str] | None = None,
    ) -> str:
        payload: dict[str, Any] = {"ticketId": ticket_id, "content": content}
        if confidence is not None:
            payload["confidence"] = confidence
        if reason:
            payload["reason"] = reason
        if risk_flags:
            payload["riskFlags"] = risk_flags

        pending_action = PendingAction(
            tool_name="save_ai_reply",
            args={"ticket_id": ticket_id, "content": content},
            summary=f"保存 {ticket_id} 号工单的 AI 回复建议",
            action_type="write",
        )
        self.create_java_pending_action(
            auth_token=auth_token,
            conversation_id=conversation_id,
            action_type=AiPendingActionType.SAVE_AI_REPLY,
            payload=payload,
        )
        return self.format_save_ai_reply_confirmation(pending_action)

    def _handle_reply_suggestion_intent(
        self,
        intent_result: IntentResult,
        auth_token: str | None,
    ) -> str:
        logger.info("Agent intent detected: reply_suggestion")
        if intent_result.missing_fields:
            return "请提供要生成回复建议的工单 ID。"

        try:
            result = self.ticket_ai_service.generate_reply_suggestion_by_ticket_id(
                int(intent_result.ticket_id),
                auth_token=auth_token,
            )
        except AppException as exc:
            logger.exception("Reply suggestion intent failed")
            return exc.message

        return json.dumps(result.model_dump(mode="json"), ensure_ascii=False)

    def _handle_save_ai_reply_intent(
        self,
        intent_result: IntentResult,
        conversation_id: str,
        auth_token: str | None,
    ) -> str:
        logger.info("Agent intent detected: save_ai_reply")
        if intent_result.missing_fields:
            field_names = {
                "ticket_id": "工单 ID",
                "reply_content": "要保存的回复建议内容",
            }
            missing_text = "、".join(
                field_names.get(field, field) for field in intent_result.missing_fields
            )
            return f"保存 AI 回复建议还缺少必要信息：{missing_text}。请补充后我再发起确认。"

        return self.create_pending_save_ai_reply_action(
            auth_token=auth_token,
            conversation_id=conversation_id,
            ticket_id=int(intent_result.ticket_id),
            content=str(intent_result.reply_content),
        )

    def _handle_grounded_ticket_ai_intent(
        self,
        intent_result: IntentResult,
        auth_token: str | None,
    ) -> str:
        label = GROUNDED_TICKET_AI_LABELS.get(intent_result.intent, "AI 分析")
        logger.info("Agent intent detected: %s", intent_result.intent.value)
        if intent_result.missing_fields:
            return f"{label}还缺少必要信息：工单 ID。请说明要分析几号工单。"

        handlers = {
            IntentType.TICKET_SUMMARY: self.ticket_ai_service.generate_ticket_summary_by_ticket_id,
            IntentType.PRIORITY_SUGGESTION: self.ticket_ai_service.suggest_priority_by_ticket_id,
            IntentType.CATEGORY_SUGGESTION: self.ticket_ai_service.suggest_category_by_ticket_id,
            IntentType.SIMILAR_TICKET_SEARCH: self.ticket_ai_service.search_similar_tickets_by_ticket_id,
            IntentType.SLA_RISK_CHECK: self.ticket_ai_service.check_sla_risk_by_ticket_id,
        }
        handler = handlers[intent_result.intent]
        try:
            result = handler(
                int(intent_result.ticket_id),
                auth_token=auth_token,
            )
        except AppException as exc:
            logger.exception("Grounded ticket AI intent failed")
            return exc.message

        return json.dumps(result.model_dump(mode="json"), ensure_ascii=False)

    def handle_pending_action_approval(
        self,
        conversation_id: str,
        auth_token: str | None = None,
    ) -> str:
        try:
            result = self.java_ticket_client.confirm_pending_action(
                auth_token=auth_token,
                conversation_id=conversation_id,
            )
        except JavaApiError as exc:
            logger.info(
                "Java pending action confirm failed: conversation_id=%s status_code=%s",
                conversation_id,
                exc.status_code,
            )
            return exc.message
        return self.format_java_pending_action_confirm_answer(result)

    def handle_pending_action_cancellation(
        self,
        conversation_id: str,
        auth_token: str | None = None,
    ) -> str:
        try:
            pending_action = self.java_ticket_client.cancel_pending_action(
                auth_token=auth_token,
                conversation_id=conversation_id,
            )
        except JavaApiError as exc:
            logger.info(
                "Java pending action cancel failed: conversation_id=%s status_code=%s",
                conversation_id,
                exc.status_code,
            )
            return exc.message

        if pending_action.actionType == AiPendingActionType.CREATE_TICKET:
            return "已取消创建工单，本次操作未执行。"
        if pending_action.actionType == AiPendingActionType.UPDATE_TICKET_STATUS:
            return "已取消修改工单状态，本次操作未执行。"
        if pending_action.actionType == AiPendingActionType.SAVE_AI_REPLY:
            return "已取消保存 AI 回复建议，本次操作未执行。"
        return "已取消待确认操作，本次操作未执行。"

    def format_java_pending_action_confirm_answer(
        self,
        result: AiPendingActionConfirmResult,
    ) -> str:
        action_type = result.pendingAction.actionType
        payload = result.pendingAction.payload
        business_result = result.result

        if action_type == AiPendingActionType.CREATE_TICKET:
            ticket = business_result if isinstance(business_result, dict) else {}
            return (
                f"已创建工单：ID {ticket.get('id')}，标题：{ticket.get('title')}，"
                f"优先级 {ticket.get('priority')}，状态 {ticket.get('status')}。"
            )

        if action_type == AiPendingActionType.UPDATE_TICKET_STATUS:
            if isinstance(business_result, dict):
                ticket_id = business_result.get("id") or payload.get("ticketId")
                old_status = business_result.get("oldStatus") or business_result.get("old_status")
                new_status = business_result.get("newStatus") or business_result.get("new_status")
                if old_status:
                    return f"已将 {ticket_id} 号工单状态从 {old_status} 修改为 {new_status}。"
                return f"已将 {ticket_id} 号工单状态修改为 {new_status}。"
            return "工单状态已修改。"

        if action_type == AiPendingActionType.SAVE_AI_REPLY:
            reply = business_result if isinstance(business_result, dict) else {}
            ticket_id = reply.get("ticketId") or payload.get("ticketId")
            reply_id = reply.get("id")
            return f"已保存 {ticket_id} 号工单的 AI 回复建议，回复 ID {reply_id}。"

        return "待确认操作已执行。"

    def _format_capability_answer(self, tool_result: dict[str, Any]) -> str:
        current_stage = tool_result.get("current_stage", "LangChain Tool 基础阶段")
        return (
            f"我是智能工单助手，当前处于 {current_stage}。"
            "现在我可以说明自己的能力，后续会逐步支持：查询工单、创建工单、"
            "修改工单状态、总结工单，以及结合知识库生成处理建议。"
        )

    def format_search_tickets_answer(
        self, result: dict[str, Any], params: dict[str, str]
    ) -> str:
        if result.get("success") is False:
            return self.format_tool_error_answer(result)

        items = result.get("items", [])
        if not isinstance(items, list):
            items = []

        total = result.get("total", len(items))
        if not isinstance(total, int):
            total = len(items)

        if total == 0:
            conditions = self._format_search_conditions(params)
            if conditions:
                return f"没有找到符合条件的工单。当前查询条件为：{conditions}。"
            return "没有找到工单。"

        display_items = items[:10]
        lines = [f"共找到 {total} 个工单："]
        for index, ticket in enumerate(display_items, start=1):
            lines.append(
                f"{index}. ID {ticket['id']}：{ticket['title']}，"
                f"优先级 {ticket['priority']}，状态 {ticket['status']}"
            )

        if total > len(display_items):
            lines.append("仅展示前 10 条。")

        return "\n".join(lines)

    def _format_search_conditions(self, params: dict[str, str]) -> str:
        ordered_keys = ("priority", "status", "keyword")
        conditions = [
            f"{key}={params[key]}" for key in ordered_keys if params.get(key)
        ]
        return ", ".join(conditions)

    def format_create_ticket_answer(self, result: dict[str, Any]) -> str:
        if result.get("success") is False:
            return self.format_tool_error_answer(result)

        ticket = result.get("ticket", {})
        if not isinstance(ticket, dict):
            raise AppException(
                code=TOOL_CALL_FAILED,
                message="工单创建工具调用失败，请稍后重试",
                status_code=500,
            )

        return (
            f"已创建工单：ID {ticket['id']}，标题：{ticket['title']}，"
            f"优先级 {ticket['priority']}，状态 {ticket['status']}。"
        )

    def format_missing_create_ticket_fields_answer(
        self, missing_fields: list[str]
    ) -> str:
        field_names = {
            "title": "标题",
            "description": "描述",
            "priority": "优先级",
        }
        if len(missing_fields) > 1:
            return "请补充工单标题、描述和优先级。"
        missing_text = "、".join(field_names[field] for field in missing_fields)
        if missing_fields == ["priority"]:
            return (
                "创建工单还缺少必要信息：优先级。请说明是 LOW、MEDIUM、HIGH "
                "还是 URGENT，或者用中文说明低/中/高/紧急。"
            )
        return f"创建工单还缺少必要信息：{missing_text}。请补充后我再创建。"

    def format_invalid_create_ticket_priority_answer(self) -> str:
        return (
            "优先级不合法，请使用 LOW、MEDIUM、HIGH、URGENT，"
            "或使用中文低/中/高/紧急。"
        )

    def format_create_ticket_confirmation(self, pending_action: PendingAction) -> str:
        args = pending_action.args
        return (
            "请确认：是否创建以下工单？\n"
            f"标题：{args['title']}\n"
            f"描述：{args['description']}\n"
            f"优先级：{args['priority']}\n"
            "状态：OPEN\n\n"
            "回复“确认”执行，回复“取消”放弃。"
        )

    def format_update_ticket_status_answer(self, result: dict[str, Any]) -> str:
        if not result.get("success"):
            return self.format_update_ticket_status_error_answer(result)

        if result.get("old_status"):
            return (
                f"已将 {result['id']} 号工单状态从 {result['old_status']} "
                f"修改为 {result['new_status']}。"
            )

        return f"已将 {result['id']} 号工单状态修改为 {result['new_status']}。"

    def format_update_ticket_status_error_answer(
        self, error: dict[str, Any] | str
    ) -> str:
        if isinstance(error, str):
            return error
        message = error.get("message")
        if isinstance(message, str) and message:
            return message
        return "工单状态修改失败，请检查工单 ID 和目标状态。"

    def format_missing_update_ticket_status_fields_answer(
        self, missing_fields: list[str]
    ) -> str:
        if missing_fields == ["ticket_id"]:
            return "修改工单状态还缺少必要信息：工单 ID。请说明要修改几号工单。"
        if missing_fields in (["status"], ["target_status"]):
            return (
                "修改工单状态还缺少必要信息：目标状态。请说明要改为 "
                "PROCESSING 或 CLOSED，也可以用中文说处理中、已完成或已关闭。"
            )
        return "请说明要修改的工单 ID 和目标状态。"

    def format_invalid_update_ticket_status_answer(self) -> str:
        return (
            "目标状态不合法。当前支持的状态包括：OPEN、PROCESSING、CLOSED；"
            "常用中文包括：未处理、处理中、已完成、已关闭。"
        )

    def format_update_ticket_status_confirmation(
        self, pending_action: PendingAction
    ) -> str:
        args = pending_action.args
        return (
            f"请确认：是否将 {args['ticket_id']} 号工单状态修改为 "
            f"{args['status']}？\n\n"
            "回复“确认”执行，回复“取消”放弃。"
        )

    def format_save_ai_reply_confirmation(self, pending_action: PendingAction) -> str:
        args = pending_action.args
        return (
            f"请确认：是否保存 {args['ticket_id']} 号工单的 AI 回复建议？\n"
            f"内容：{args['content']}\n\n"
            "回复“确认”执行，回复“取消”放弃。"
        )

    def format_tool_error_answer(self, result: dict[str, Any]) -> str:
        message = result.get("message")
        if isinstance(message, str) and message:
            return message
        return "工具调用失败，请稍后重试。"

    def _safe_tool_params(self, params: dict[str, Any]) -> dict[str, Any]:
        safe_params = params.copy()
        if safe_params.get("auth_token"):
            safe_params["auth_token"] = "***"
        return safe_params

    def _resolve_conversation_id(
        self,
        user_id: str | None,
        conversation_id: str | None,
    ) -> str:
        if conversation_id and conversation_id.strip():
            return conversation_id.strip()
        if user_id and str(user_id).strip():
            return f"user-{str(user_id).strip()}"
        return DEFAULT_SESSION_KEY

    def _strip_sensitive_payload(self, payload: dict[str, Any]) -> dict[str, Any]:
        safe_payload = {}
        for key, value in payload.items():
            normalized_key = key.strip().lower()
            if "token" in normalized_key or normalized_key == "authorization":
                continue
            safe_payload[key] = value
        return safe_payload

    def _has_explicit_priority_text(self, message: str) -> bool:
        return "优先级" in message

    def _has_explicit_status_update_text(self, message: str) -> bool:
        return any(trigger in message for trigger in ("改成", "改为", "修改为", "更新为"))
