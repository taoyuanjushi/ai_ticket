from pydantic import BaseModel


class ToolInfo(BaseModel):
    name: str
    description: str


class ToolListResponse(BaseModel):
    tools: list[ToolInfo]
