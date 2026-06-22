from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse

from app.api.agent_api import router as agent_router
from app.api.ticket_ai_api import router as ticket_ai_router
from app.core.config import settings
from app.core.exceptions import AppException, INTERNAL_ERROR, INVALID_PARAMS
from app.core.logger import get_logger, setup_logging

setup_logging()
logger = get_logger(__name__)

app = FastAPI(title=settings.app_name)


@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "code": exc.code,
            "message": exc.message,
            "detail": exc.detail,
        },
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={
            "success": False,
            "code": INVALID_PARAMS,
            "message": "请求参数不合法",
            "detail": jsonable_encoder(exc.errors()),
        },
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled application error")
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "code": INTERNAL_ERROR,
            "message": "系统内部错误，请稍后重试",
            "detail": None,
        },
    )


app.include_router(agent_router)
app.include_router(ticket_ai_router)
