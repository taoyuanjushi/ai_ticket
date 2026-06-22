import re
from collections.abc import Callable

from app.clients.java_ticket_client import JavaApiError, JavaTicketClient
from app.clients.llm_client import LLMClient, LLMError
from app.core.exceptions import (
    AppException,
    JAVA_API_ERROR,
    LLM_CALL_FAILED,
    LLM_CONFIG_MISSING,
)
from app.core.logger import get_logger
from app.prompts.reply_suggestion_prompt import build_reply_suggestion_prompt
from app.schemas.ticket_ai_schema import (
    CategorySuggestionResult,
    PrioritySuggestionResult,
    ReplySuggestionResult,
    SimilarTicketSearchResult,
    SlaRiskResult,
    TicketSummaryResult,
)
from app.schemas.ticket import TicketDetailDTO
from app.services.ai_capabilities import (
    CategorySuggestionService,
    PrioritySuggestionService,
    SimilarTicketSearchService,
    SlaRiskService,
    TicketSummaryService,
)
from app.services.grounding import TicketGroundingService
from pydantic import ValidationError

logger = get_logger(__name__)

REPLY_SUGGESTION_FAILED_MESSAGE = "AI 回复建议生成失败，请稍后重试或手动填写。"


class TicketAiService:
    """工单 AI 服务，负责基于真实工单详情生成受保护的 AI 结果。"""

    def __init__(
        self,
        llm_client: LLMClient | None = None,
        java_ticket_client: JavaTicketClient | None = None,
        grounding_service: TicketGroundingService | None = None,
    ) -> None:
        self.llm_client = llm_client or LLMClient()
        self.java_ticket_client = java_ticket_client
        self.grounding_service = grounding_service or TicketGroundingService()
        self.summary_service = TicketSummaryService(
            java_ticket_client=java_ticket_client,
            grounding_service=self.grounding_service,
        )
        self.priority_service = PrioritySuggestionService(
            java_ticket_client=java_ticket_client,
            grounding_service=self.grounding_service,
        )
        self.category_service = CategorySuggestionService(
            java_ticket_client=java_ticket_client,
            grounding_service=self.grounding_service,
        )
        self.similar_service = SimilarTicketSearchService(
            java_ticket_client=java_ticket_client,
            grounding_service=self.grounding_service,
        )
        self.sla_service = SlaRiskService(
            java_ticket_client=java_ticket_client,
            grounding_service=self.grounding_service,
        )

    def generate_reply_suggestion_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> ReplySuggestionResult:
        return self._generate_grounded_ticket_result(
            ticket_id=ticket_id,
            auth_token=auth_token,
            task_name="回复建议",
            task_instruction=(
                "请基于 ticket_detail 生成给客服人员参考的回复建议。"
                "只生成回复建议，不执行创建、修改、删除或保存操作。"
            ),
            prompt_builder=build_reply_suggestion_prompt,
        )

    def generate_ticket_summary_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> TicketSummaryResult:
        return self.summary_service.summarize(
            auth_token=auth_token,
            ticket_id=ticket_id,
        )

    def suggest_priority_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> PrioritySuggestionResult:
        return self.priority_service.suggest(
            auth_token=auth_token,
            ticket_id=ticket_id,
        )

    def suggest_category_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> CategorySuggestionResult:
        return self.category_service.suggest(
            auth_token=auth_token,
            ticket_id=ticket_id,
        )

    def search_similar_tickets_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> SimilarTicketSearchResult:
        return self.similar_service.search(
            auth_token=auth_token,
            ticket_id=ticket_id,
        )

    def check_sla_risk_by_ticket_id(
        self,
        ticket_id: int,
        auth_token: str | None = None,
    ) -> SlaRiskResult:
        return self.sla_service.check(
            auth_token=auth_token,
            ticket_id=ticket_id,
        )

    def _generate_grounded_ticket_result(
        self,
        ticket_id: int,
        auth_token: str | None,
        task_name: str,
        task_instruction: str,
        prompt_builder: Callable[[TicketDetailDTO], str] | None = None,
    ) -> ReplySuggestionResult:
        ticket_detail = self._get_ticket_detail(ticket_id, auth_token)
        if prompt_builder:
            prompt = prompt_builder(ticket_detail)
        else:
            prompt = self.grounding_service.build_grounding_prompt(
                ticket_detail=ticket_detail,
                task_name=task_name,
                task_instruction=task_instruction,
            )

        try:
            generated_content = self.llm_client.generate_text(prompt)
        except LLMError as exc:
            logger.exception("%s LLM call failed", task_name)
            message = str(exc)
            if "配置不完整" in message:
                raise AppException(
                    code=LLM_CONFIG_MISSING,
                    message=message,
                    status_code=400,
                ) from exc
            raise AppException(
                code=LLM_CALL_FAILED,
                message=f"生成 AI {task_name}失败：{message}",
                status_code=502,
            ) from exc

        result = self._parse_llm_result(generated_content)
        return self.grounding_service.apply_grounding(
            result=result,
            ticket_detail=ticket_detail,
            task_name=task_name,
        )

    def _get_ticket_detail(
        self,
        ticket_id: int,
        auth_token: str | None,
    ) -> TicketDetailDTO:
        try:
            java_ticket_client = self.java_ticket_client or JavaTicketClient()
            return java_ticket_client.get_ticket_detail(
                auth_token=auth_token,
                ticket_id=ticket_id,
            )
        except JavaApiError as exc:
            raise AppException(
                code=JAVA_API_ERROR,
                message=exc.message,
                status_code=exc.status_code,
            ) from exc

    def _parse_llm_result(self, content: str) -> ReplySuggestionResult:
        try:
            return ReplySuggestionResult.model_validate_json(content)
        except (ValidationError, ValueError):
            repaired_content = self._repair_json_content(content)

        if repaired_content != content:
            try:
                return ReplySuggestionResult.model_validate_json(repaired_content)
            except (ValidationError, ValueError) as exc:
                logger.warning("Reply suggestion JSON repair failed: %s", exc)

        raise AppException(
            code=LLM_CALL_FAILED,
            message=REPLY_SUGGESTION_FAILED_MESSAGE,
            status_code=502,
        )

    def _repair_json_content(self, content: str) -> str:
        text = content.strip()
        text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.IGNORECASE)
        text = re.sub(r"\s*```$", "", text)

        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end > start:
            return text[start : end + 1].strip()
        return text

    def _apply_ticket_detail_risk_controls(
        self,
        result: ReplySuggestionResult,
        ticket_detail: TicketDetailDTO,
    ) -> ReplySuggestionResult:
        return self.grounding_service.apply_grounding(
            result=result,
            ticket_detail=ticket_detail,
            task_name="回复建议",
        )

    def _is_ticket_detail_insufficient(self, ticket_detail: TicketDetailDTO) -> bool:
        return self.grounding_service.is_information_insufficient(ticket_detail)
