from langchain_core.tools import BaseTool

from app.tools.capability_tools import get_agent_capabilities
from app.tools.ticket_tools import create_ticket, search_tickets, update_ticket_status


def get_all_tools() -> list[BaseTool]:
    return [get_agent_capabilities, search_tickets, create_ticket, update_ticket_status]
