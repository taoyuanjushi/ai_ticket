from typing import Any

from langchain_core.tools import tool

from app.clients.java_ticket_client import JavaApiError, JavaTicketClient
from app.core.exceptions import (
    AppException,
    TOOL_CALL_FAILED,
)
from app.core.logger import get_logger
from app.schemas.ticket_tool_schema import (
    CreateTicketInput,
    SearchTicketsInput,
    UpdateTicketStatusInput,
)
from app.schemas.ticket import (
    CreateTicketRequest,
    TicketDTO,
    UpdateTicketStatusRequest,
)

logger = get_logger(__name__)

SEARCH_TICKETS_TOOL_DESCRIPTION = (
    "Search real Java backend tickets by status, priority, or keyword. Use this tool when the "
    "user wants to find, list, filter, or search tickets. This tool only reads "
    "ticket data through the Java API and does not create, update, or delete tickets."
)
CREATE_TICKET_TOOL_DESCRIPTION = (
    "Create a new ticket through the Java backend with title, description, and priority. Use this tool "
    "only when the user clearly wants to create or submit a new ticket and provides "
    "all required fields: title, description, and priority. Do not use this tool if "
    "any required field is missing. This tool changes Java backend data and requires "
    "user confirmation before execution."
)
UPDATE_TICKET_STATUS_TOOL_DESCRIPTION = (
    "Update the status of an existing Java backend ticket. Use this tool only when the user "
    "wants to change a ticket status and provides a ticket id and target status. "
    "This tool changes Java backend data, relies on Java business validation, and requires "
    "user confirmation before execution."
)

ALLOWED_STATUS_TRANSITIONS: dict[str, list[str]] = {
    "OPEN": ["PROCESSING", "CLOSED"],
    "PROCESSING": ["CLOSED"],
    "CLOSED": [],
}

STATUS_TEXT_MAPPINGS: tuple[tuple[str, str], ...] = (
    ("processing", "PROCESSING"),
    ("未处理", "OPEN"),
    ("待处理", "OPEN"),
    ("处理中", "PROCESSING"),
    ("处理完了", "CLOSED"),
    ("已完成", "CLOSED"),
    ("已关闭", "CLOSED"),
    ("open", "OPEN"),
    ("done", "CLOSED"),
    ("closed", "CLOSED"),
    ("打开", "OPEN"),
    ("处理", "PROCESSING"),
    ("完成", "CLOSED"),
    ("关闭", "CLOSED"),
)

PRIORITY_TEXT_MAPPINGS: tuple[tuple[str, str], ...] = (
    ("非常紧急", "URGENT"),
    ("低优先级", "LOW"),
    ("中优先级", "MEDIUM"),
    ("高优先级", "HIGH"),
    ("medium", "MEDIUM"),
    ("urgent", "URGENT"),
    ("low", "LOW"),
    ("high", "HIGH"),
    ("普通", "MEDIUM"),
    ("紧急", "URGENT"),
    ("低", "LOW"),
    ("中", "MEDIUM"),
    ("高", "HIGH"),
)


def normalize_status_text(value: str) -> str | None:
    normalized_value = value.strip().lower()
    for keyword, status in STATUS_TEXT_MAPPINGS:
        if normalized_value == keyword:
            return status
    return None


def normalize_status(text: str) -> str | None:
    return normalize_status_text(text)


def normalize_priority_text(value: str) -> str | None:
    normalized_value = value.strip().lower()
    for keyword, priority in PRIORITY_TEXT_MAPPINGS:
        if normalized_value == keyword:
            return priority
    return None


def extract_status_from_text(text: str) -> str | None:
    normalized_text = text.strip().lower()
    for keyword, status in STATUS_TEXT_MAPPINGS:
        if keyword in normalized_text:
            return status
    return None


def find_ticket_by_id(ticket_id: int, auth_token: str | None = None) -> dict[str, Any] | None:
    try:
        ticket = JavaTicketClient().get_ticket_by_id(
            ticket_id=ticket_id,
            auth_token=auth_token,
        )
    except JavaApiError:
        return None

    return _ticket_to_tool_dict(ticket)


def is_valid_status_transition(old_status: str, new_status: str) -> bool:
    return new_status in ALLOWED_STATUS_TRANSITIONS.get(old_status, [])


def extract_priority_from_text(text: str) -> str | None:
    normalized_text = text.strip().lower()

    for keyword, priority in PRIORITY_TEXT_MAPPINGS:
        if len(keyword) == 1:
            continue
        if keyword in normalized_text:
            return priority

    for keyword, priority in PRIORITY_TEXT_MAPPINGS:
        if len(keyword) != 1 or keyword not in normalized_text:
            continue
        if keyword == "中" and "处理中" in normalized_text:
            continue
        return priority

    return None


@tool(
    "search_tickets",
    args_schema=SearchTicketsInput,
    description=SEARCH_TICKETS_TOOL_DESCRIPTION,
)
def search_tickets(
    status: str | None = None,
    priority: str | None = None,
    category: str | None = None,
    keyword: str | None = None,
    page: int = 1,
    size: int = 10,
    auth_token: str | None = None,
) -> dict[str, Any]:
    try:
        params = SearchTicketsInput(
            status=status,
            priority=priority,
            category=category,
            keyword=keyword,
            page=page,
            size=size,
            auth_token=auth_token,
        )
        tickets = JavaTicketClient().search_tickets(
            auth_token=params.auth_token,
            query={
                "status": params.status,
                "priority": params.priority,
                "category": params.category,
                "keyword": params.keyword,
                "page": params.page,
                "size": params.size,
            },
        )

        items = [_ticket_to_tool_dict(ticket) for ticket in tickets]
        return {
            "success": True,
            "total": len(items),
            "items": items,
        }
    except JavaApiError as exc:
        return _java_api_error_result(str(exc))
    except AppException:
        raise
    except Exception as exc:
        logger.exception("Search tickets tool failed")
        raise AppException(
            code=TOOL_CALL_FAILED,
            message="工单查询工具调用失败，请稍后重试",
            status_code=500,
        ) from exc

@tool(
    "create_ticket",
    args_schema=CreateTicketInput,
    description=CREATE_TICKET_TOOL_DESCRIPTION,
)
def create_ticket(
    title: str,
    description: str,
    priority: str,
    category: str | None = None,
    auth_token: str | None = None,
) -> dict[str, Any]:
    try:
        params = CreateTicketInput(
            title=title,
            description=description,
            priority=priority,
            category=category,
            auth_token=auth_token,
        )
        ticket = JavaTicketClient().create_ticket(
            auth_token=params.auth_token,
            req=CreateTicketRequest(
                title=params.title,
                description=params.description,
                priority=params.priority,
                category=params.category,
            ),
        )
        return {
            "success": True,
            "ticket": _ticket_to_tool_dict(ticket),
            "message": "工单创建成功",
        }
    except JavaApiError as exc:
        return _java_api_error_result(str(exc))
    except AppException:
        raise
    except Exception as exc:
        logger.exception("Create ticket tool failed")
        raise AppException(
            code=TOOL_CALL_FAILED,
            message="工单创建工具调用失败，请稍后重试",
            status_code=500,
        ) from exc


@tool(
    "update_ticket_status",
    args_schema=UpdateTicketStatusInput,
    description=UPDATE_TICKET_STATUS_TOOL_DESCRIPTION,
)
def update_ticket_status(
    ticket_id: int,
    status: str,
    auth_token: str | None = None,
) -> dict[str, Any]:
    try:
        params = UpdateTicketStatusInput(
            ticket_id=ticket_id,
            status=status,
            auth_token=auth_token,
        )
        client = JavaTicketClient()
        try:
            old_ticket = client.get_ticket_by_id(
                ticket_id=params.ticket_id,
                auth_token=params.auth_token,
            )
        except JavaApiError:
            old_ticket = None

        new_ticket = client.update_ticket_status(
            auth_token=params.auth_token,
            ticket_id=params.ticket_id,
            req=UpdateTicketStatusRequest(status=params.status),
        )
        old_status = None
        if old_ticket is not None:
            old_status = old_ticket.status.value

        return {
            "success": True,
            "id": params.ticket_id,
            "old_status": old_status,
            "new_status": new_ticket.status.value,
            "ticket": _ticket_to_tool_dict(new_ticket),
            "message": "工单状态修改成功",
        }
    except JavaApiError as exc:
        return _java_api_error_result(str(exc))
    except AppException:
        raise
    except Exception as exc:
        logger.exception("Update ticket status tool failed")
        raise AppException(
            code=TOOL_CALL_FAILED,
            message="工单状态修改工具调用失败，请稍后重试",
            status_code=500,
        ) from exc


def _ticket_to_tool_dict(ticket: TicketDTO) -> dict[str, Any]:
    return ticket.model_dump(mode="json", exclude_none=True)


def _java_api_error_result(message: str) -> dict[str, Any]:
    return {
        "success": False,
        "code": "JAVA_API_ERROR",
        "message": message,
    }
