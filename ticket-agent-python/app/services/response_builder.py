from typing import Any

from app.schemas.agent_response import AgentResponse


class ResponseBuilder:
    @staticmethod
    def json_result(
        message: str,
        data: Any,
        risk_flags: list[str] | None = None,
    ) -> AgentResponse:
        payload = ResponseBuilder._to_payload(data)
        data_risk_flags = []
        if isinstance(payload, dict):
            value = payload.get("risk_flags")
            if isinstance(value, list):
                data_risk_flags = [item for item in value if isinstance(item, str) and item]

        return AgentResponse(
            type="JSON_RESULT",
            message=message,
            data=payload,
            risk_flags=ResponseBuilder._merge_risk_flags(
                risk_flags or [],
                data_risk_flags,
            ),
        )

    @staticmethod
    def normal(message: str) -> AgentResponse:
        return AgentResponse(type="NORMAL", message=message)

    @staticmethod
    def missing_fields(
        message: str,
        missing_fields: list[str],
        collected: dict[str, Any] | None = None,
    ) -> AgentResponse:
        return AgentResponse(
            type="MISSING_FIELDS",
            message=message,
            data={
                "missing_fields": list(missing_fields),
                "collected": collected or {},
            },
        )

    @staticmethod
    def pending_confirmation(
        message: str,
        action_type: str,
        payload: dict[str, Any],
        ticket_id: int | None = None,
    ) -> AgentResponse:
        data: dict[str, Any] = {
            "actionType": action_type,
            "payload": payload,
        }
        if ticket_id is not None:
            data["ticketId"] = ticket_id
        return AgentResponse(
            type="PENDING_CONFIRMATION",
            message=message,
            data=data,
        )

    @staticmethod
    def unauthorized(message: str = "登录状态已失效，请重新登录。") -> AgentResponse:
        return AgentResponse(type="UNAUTHORIZED", message=message)

    @staticmethod
    def forbidden(message: str = "你没有权限执行该操作。") -> AgentResponse:
        return AgentResponse(type="FORBIDDEN", message=message)

    @staticmethod
    def error(
        message: str,
        risk_flags: list[str] | None = None,
    ) -> AgentResponse:
        return AgentResponse(
            type="ERROR",
            message=message,
            risk_flags=risk_flags or [],
        )

    @staticmethod
    def unknown_intent(message: str) -> AgentResponse:
        return AgentResponse(type="UNKNOWN_INTENT", message=message)

    @staticmethod
    def from_status_error(
        status_code: int,
        message: str,
        risk_flags: list[str] | None = None,
    ) -> AgentResponse:
        if status_code == 401:
            return ResponseBuilder.unauthorized(message)
        if status_code in (403, 404):
            return ResponseBuilder.forbidden(message)
        return ResponseBuilder.error(message, risk_flags=risk_flags)

    @staticmethod
    def _to_payload(data: Any) -> Any:
        if hasattr(data, "model_dump"):
            return data.model_dump(mode="json")
        return data

    @staticmethod
    def _merge_risk_flags(*flag_groups: list[str]) -> list[str]:
        merged: list[str] = []
        for flags in flag_groups:
            for flag in flags:
                if flag not in merged:
                    merged.append(flag)
        return merged
