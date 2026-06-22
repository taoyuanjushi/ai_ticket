from copy import deepcopy
from typing import Any


INITIAL_MOCK_TICKETS: list[dict[str, Any]] = [
    {
        "id": 1,
        "title": "登录失败",
        "description": "用户输入正确密码后仍无法登录",
        "priority": "HIGH",
        "status": "OPEN",
    },
    {
        "id": 2,
        "title": "文件上传异常",
        "description": "上传 PDF 时失败",
        "priority": "MEDIUM",
        "status": "PROCESSING",
    },
    {
        "id": 3,
        "title": "登录接口超时",
        "description": "用户登录时接口响应超过 10 秒",
        "priority": "HIGH",
        "status": "PROCESSING",
    },
    {
        "id": 4,
        "title": "订单状态未更新",
        "description": "用户支付成功后订单仍显示待支付",
        "priority": "URGENT",
        "status": "OPEN",
    },
    {
        "id": 5,
        "title": "历史工单归档",
        "description": "已完成工单需要归档",
        "priority": "LOW",
        "status": "CLOSED",
    },
]

MOCK_TICKETS: list[dict[str, Any]] = deepcopy(INITIAL_MOCK_TICKETS)


def get_next_ticket_id() -> int:
    if not MOCK_TICKETS:
        return 1
    return max(ticket["id"] for ticket in MOCK_TICKETS) + 1


def append_mock_ticket(ticket: dict[str, Any]) -> dict[str, Any]:
    MOCK_TICKETS.append(ticket)
    return ticket


def reset_mock_tickets() -> None:
    MOCK_TICKETS.clear()
    MOCK_TICKETS.extend(deepcopy(INITIAL_MOCK_TICKETS))
