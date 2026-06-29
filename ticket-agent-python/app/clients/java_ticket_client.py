from typing import Any

import httpx

from app.core.config import settings
from app.core.logger import get_logger
from app.schemas.java_response import JavaResult
from app.schemas.pending_action import (
    AiPendingActionConfirmResult,
    AiPendingActionDTO,
    CreateAiPendingActionRequest,
)
from app.schemas.ticket import (
    CreateTicketRequest,
    TicketDTO,
    TicketDetailDTO,
    UpdateTicketStatusRequest,
)

logger = get_logger(__name__)


class JavaApiError(Exception):
    """Java API 调用异常。"""

    def __init__(
        self,
        status_code: int,
        message: str,
        data: Any | None = None,
    ) -> None:
        self.status_code = status_code
        self.message = message
        self.data = data
        super().__init__(message)


class JavaTicketClient:
    """调用 Java Spring Boot 工单 API 的客户端。"""

    def __init__(
        self,
        base_url: str | None = None,
        token: str | None = None,
        timeout: float | None = None,
    ) -> None:
        self.base_url = (base_url or settings.java_api_base_url).strip().rstrip("/")
        self.token = token or settings.java_api_token.strip() or None
        self.timeout = timeout if timeout is not None else settings.java_api_timeout

    def search_tickets(
        self,
        auth_token: str | None = None,
        query: dict[str, Any] | None = None,
        *,
        status: str | None = None,
        priority: str | None = None,
        category: str | None = None,
        keyword: str | None = None,
        page: int = 1,
        size: int = 10,
    ) -> list[TicketDTO]:
        params: dict[str, Any] = dict(query or {})
        params.setdefault("page", page)
        params.setdefault("size", size)
        if status:
            params["status"] = status
        if priority:
            params["priority"] = priority
        if category:
            params["category"] = category
        if keyword:
            params["keyword"] = keyword

        data = self._request("GET", "/tickets", auth_token=auth_token, params=params)
        return self._parse_ticket_list(data)

    def list_tickets(
        self,
        auth_token: str | None = None,
        query: dict[str, Any] | None = None,
    ) -> list[TicketDTO]:
        return self.search_tickets(auth_token=auth_token, query=query or {})

    def get_ticket_by_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> TicketDTO:
        data = self._request("GET", f"/tickets/{ticket_id}", auth_token=auth_token)
        return TicketDTO.model_validate(data)

    def get_ticket_detail(
        self,
        auth_token: str | int | None = None,
        ticket_id: int | None = None,
        token: str | None = None,
    ) -> TicketDetailDTO:
        # Backward-compatible path for previous calls: get_ticket_detail(3, token="...")
        if isinstance(auth_token, int) and ticket_id is None:
            ticket_id = auth_token
            auth_token = token
        if ticket_id is None:
            raise ValueError("ticket_id不能为空")

        data = self._request(
            "GET",
            f"/tickets/{ticket_id}/detail",
            auth_token=auth_token if isinstance(auth_token, str) else None,
        )
        return TicketDetailDTO.from_java_detail(data)

    def create_ticket(
        self,
        auth_token: str | None = None,
        req: CreateTicketRequest | None = None,
        *,
        title: str | None = None,
        content: str | None = None,
        priority: str | None = None,
        category: str | None = None,
    ) -> TicketDTO:
        if req is None:
            if title is None or content is None or priority is None:
                raise ValueError("title、content、priority不能为空")
            req = CreateTicketRequest(
                title=title,
                description=content,
                priority=priority,
                category=category,
            )

        data = self._request(
            "POST",
            "/tickets",
            auth_token=auth_token,
            json=req.to_java_body(),
        )
        return TicketDTO.model_validate(data)

    def update_ticket_status(
        self,
        auth_token: str | int | None = None,
        ticket_id: int | str | None = None,
        req: UpdateTicketStatusRequest | None = None,
        *,
        status: str | None = None,
    ) -> TicketDTO:
        # Backward-compatible path for previous calls: update_ticket_status(1, "PROCESSING")
        if isinstance(auth_token, int) and isinstance(ticket_id, str) and req is None:
            status = ticket_id
            ticket_id = auth_token
            auth_token = None
        if req is None:
            if status is None:
                raise ValueError("status不能为空")
            req = UpdateTicketStatusRequest(status=status)
        if not isinstance(ticket_id, int):
            raise ValueError("ticket_id不能为空")

        data = self._request(
            "PUT",
            f"/tickets/{ticket_id}/status",
            auth_token=auth_token if isinstance(auth_token, str) else None,
            json=req.to_java_body(),
        )
        return TicketDTO.model_validate(data)

    def create_pending_action(
        self,
        auth_token: str | None,
        req: CreateAiPendingActionRequest,
    ) -> AiPendingActionDTO:
        data = self._request(
            "POST",
            "/ai/pending-actions",
            auth_token=auth_token,
            json=req.model_dump(mode="json"),
        )
        return AiPendingActionDTO.model_validate(data)

    def get_current_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionDTO | None:
        data = self._request(
            "GET",
            "/ai/pending-actions/current",
            auth_token=auth_token,
            params={"conversationId": conversation_id},
        )
        if data is None:
            return None
        return AiPendingActionDTO.model_validate(data)

    def confirm_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionConfirmResult:
        data = self._request(
            "POST",
            "/ai/pending-actions/confirm",
            auth_token=auth_token,
            json={"conversationId": conversation_id},
        )
        return AiPendingActionConfirmResult.model_validate(data)

    def cancel_pending_action(
        self,
        auth_token: str | None,
        conversation_id: str,
    ) -> AiPendingActionDTO:
        data = self._request(
            "POST",
            "/ai/pending-actions/cancel",
            auth_token=auth_token,
            json={"conversationId": conversation_id},
        )
        return AiPendingActionDTO.model_validate(data)

    def _headers(self, auth_token: str | None = None) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        auth_token = auth_token or self.token
        if not auth_token:
            raise JavaApiError(401, "登录状态已失效，请重新登录。")

        if auth_token.startswith("Bearer "):
            headers["Authorization"] = auth_token
        else:
            headers["Authorization"] = f"Bearer {auth_token}"
        return headers

    def _request(
        self,
        method: str,
        path: str,
        auth_token: str | None = None,
        params: dict[str, Any] | None = None,
        json: dict[str, Any] | None = None,
    ) -> Any:
        url = f"{self.base_url}{path}"
        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.request(
                    method,
                    url,
                    params=params,
                    json=json,
                    headers=self._headers(auth_token=auth_token),
                )
                logger.info(
                    "Java API request completed: method=%s path=%s status_code=%s",
                    method,
                    path,
                    response.status_code,
                )
        except httpx.TimeoutException as exc:
            logger.exception("Java API request timed out: method=%s path=%s", method, path)
            raise JavaApiError(
                504,
                "Java 后端响应超时，请稍后重试。",
            ) from exc
        except httpx.RequestError as exc:
            logger.exception("Java API request failed: method=%s path=%s", method, path)
            raise JavaApiError(
                502,
                "无法连接 Java 后端服务，请确认 Java 后端已启动。",
            ) from exc

        return self._unwrap_result(response)

    def _unwrap_result(self, response: httpx.Response) -> Any:
        payload, is_json = self._parse_response_json(response)

        if not is_json:
            data = {"raw_response": self._safe_response_text(response)}
            if not 200 <= response.status_code < 300:
                raise self._build_error(response.status_code, None, data)
            raise JavaApiError(
                response.status_code,
                "Java API 返回非 JSON 响应，请检查后端接口。",
                data,
            )

        if not 200 <= response.status_code < 300:
            message = self._extract_message(payload, response.text)
            raise self._build_error(response.status_code, message, payload)

        if isinstance(payload, dict) and "code" in payload:
            result = JavaResult.model_validate(payload)
            if result.code == 200:
                return result.data
            raise self._build_error(result.code, result.message, result.data)

        return payload

    def _parse_response_json(self, response: httpx.Response) -> tuple[Any, bool]:
        try:
            return response.json(), True
        except ValueError:
            return {}, False

    def _safe_response_text(self, response: httpx.Response) -> str:
        text = response.text or ""
        return text[:500]

    def _extract_message(self, payload: Any, fallback: str) -> str:
        if isinstance(payload, dict):
            for key in ("message", "msg", "error", "detail", "data"):
                value = payload.get(key)
                if isinstance(value, str) and value:
                    return value
        return fallback

    def _build_error(
        self,
        status_code: int,
        message: str | None,
        data: Any | None = None,
    ) -> JavaApiError:
        if status_code == 401:
            return JavaApiError(status_code, "登录状态已失效，请重新登录。", data)
        if status_code == 403:
            return JavaApiError(status_code, "你没有权限执行该操作。", data)
        if status_code == 404:
            return JavaApiError(status_code, "目标工单不存在，或你无权访问该工单。", data)
        if status_code == 400:
            return JavaApiError(status_code, message or "请求参数不正确。", data)
        if status_code >= 500:
            return JavaApiError(status_code, "服务暂时异常，请稍后重试。", data)
        return JavaApiError(
            status_code,
            message or f"Java API 调用失败：code={status_code}",
            data,
        )

    def _parse_ticket_list(self, data: Any) -> list[TicketDTO]:
        if isinstance(data, dict):
            records = data.get("records", [])
        elif isinstance(data, list):
            records = data
        else:
            records = []

        if not isinstance(records, list):
            records = []

        return [
            TicketDTO.model_validate(record)
            for record in records
            if isinstance(record, dict)
        ]
