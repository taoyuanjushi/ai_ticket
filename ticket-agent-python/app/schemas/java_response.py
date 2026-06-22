from typing import Any

from pydantic import BaseModel


class JavaResult(BaseModel):
    code: int
    message: str | None = None
    data: Any | None = None
