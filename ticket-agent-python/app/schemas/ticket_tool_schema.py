from typing import Literal

from pydantic import BaseModel, Field, field_validator


class SearchTicketsInput(BaseModel):
    status: Literal["OPEN", "PROCESSING", "CLOSED"] | None = Field(
        default=None,
        description="Ticket status. Must be one of OPEN, PROCESSING, CLOSED.",
    )
    priority: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] | None = Field(
        default=None,
        description="Ticket priority. Must be one of LOW, MEDIUM, HIGH, URGENT.",
    )
    keyword: str | None = Field(
        default=None,
        description="Keyword used to search ticket title or description.",
    )
    category: str | None = Field(
        default=None,
        description="Optional ticket category.",
    )
    page: int = Field(default=1, ge=1, description="Page number.")
    size: int = Field(default=10, ge=1, le=100, description="Page size.")
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used to call the Java API.",
    )


class CreateTicketInput(BaseModel):
    title: str = Field(
        description=(
            "Ticket title. Required. Do not invent it if the user does not provide it."
        ),
    )
    description: str = Field(
        description=(
            "Ticket description. Required. Do not invent it if the user does not "
            "provide it."
        ),
    )
    priority: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] = Field(
        description="Ticket priority. Must be one of LOW, MEDIUM, HIGH, URGENT.",
    )
    category: str | None = Field(default=None, description="Optional ticket category.")
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used to call the Java API.",
    )

    @field_validator("title", "description")
    @classmethod
    def validate_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("field cannot be empty")
        return value


class UpdateTicketStatusInput(BaseModel):
    ticket_id: int = Field(
        description="Ticket id. Required. Must be an existing ticket id.",
    )
    status: Literal["OPEN", "PROCESSING", "CLOSED"] = Field(
        description=(
            "Target ticket status. Must be one of OPEN, PROCESSING, CLOSED."
        ),
    )
    auth_token: str | None = Field(
        default=None,
        description="Optional Java JWT token used to call the Java API.",
    )
