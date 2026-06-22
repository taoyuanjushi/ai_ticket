import re

from app.schemas.intent_schema import IntentResult, IntentType
from app.tools.ticket_tools import (
    extract_priority_from_text,
    extract_status_from_text,
    normalize_priority_text,
    normalize_status,
)


CONFIRM_TRIGGERS = {
    "确认",
    "确定",
    "是的",
    "可以",
    "执行",
    "提交",
    "同意",
    "没问题",
    "approve",
    "yes",
    "ok",
}

CANCEL_TRIGGERS = {
    "取消",
    "不用",
    "算了",
    "不要",
    "不要执行",
    "先不改了",
    "拒绝",
    "cancel",
    "no",
    "reject",
}

QUERY_TICKET_TRIGGERS = (
    "查询我的工单",
    "我的工单有哪些",
    "看看工单",
    "查工单",
    "查询工单",
    "找工单",
    "工单列表",
    "有哪些工单",
    "所有工单",
    "全部工单",
    "查一下",
    "查询",
    "找一下",
    "找一找",
    "列出",
)

CREATE_TICKET_TRIGGERS = (
    "创建工单",
    "新建工单",
    "帮我提一个工单",
    "新增工单",
    "提交工单",
    "创建一个工单",
    "新增一个工单",
    "提交一个工单",
    "帮我创建",
    "帮我新增",
    "创建一个",
    "新增一个",
    "提交一个",
)

UPDATE_TICKET_ACTION_TRIGGERS = (
    "改成",
    "改为",
    "修改",
    "更新",
    "状态",
    "关闭",
    "完成",
    "已完成",
    "处理完",
)

UPDATE_TICKET_QUERY_TRIGGERS = (
    "查",
    "查询",
    "找",
    "列出",
    "有哪些",
    "所有",
    "全部",
)

REPLY_SUGGESTION_TRIGGERS = (
    "生成回复",
    "回复建议",
    "这个工单怎么回复",
    "怎么回复",
    "如何回复",
    "帮我回复",
    "回复草稿",
)

TICKET_SUMMARY_TRIGGERS = (
    "总结工单",
    "工单摘要",
    "总结一下工单",
    "总结一下",
    "总结",
    "概括工单",
    "工单内容",
)

PRIORITY_SUGGESTION_TRIGGERS = (
    "优先级建议",
    "判断优先级",
    "优先级是否合理",
    "建议优先级",
    "优先级应该是多少",
)

CATEGORY_SUGGESTION_TRIGGERS = (
    "分类建议",
    "判断分类",
    "建议分类",
    "工单分类",
    "什么分类",
    "属于什么分类",
)

SIMILAR_TICKET_TRIGGERS = (
    "相似工单",
    "类似工单",
    "有没有类似工单",
    "有没有相似工单",
    "找类似工单",
    "查类似工单",
)

SLA_RISK_TRIGGERS = (
    "sla风险",
    "sla 风险",
    "sla提醒",
    "sla 提醒",
    "超时风险",
    "风险提醒",
)

SAVE_AI_REPLY_TRIGGERS = (
    "保存回复建议",
    "保存 ai 回复建议",
    "保存AI回复建议",
    "保存建议",
    "采纳回复建议",
    "采纳 ai 回复建议",
    "采纳AI回复建议",
    "采纳建议",
)

KEYWORD_PATTERNS = (
    re.compile(r"标题(?:里)?包含\s*(?P<keyword>.+?)\s*的工单"),
    re.compile(r"包含\s*(?P<keyword>.+?)\s*的工单"),
    re.compile(r"标题(?:里)?有\s*(?P<keyword>.+?)\s*的工单"),
    re.compile(r"有\s*(?P<keyword>.+?)\s*的工单"),
)

CREATE_TITLE_PATTERN = re.compile(
    r"标题\s*(?:是|为|:|：)\s*(?P<value>.+?)"
    r"(?=(?:\s*[，,。；;]\s*(?:描述|优先级)\s*(?:是|为|:|：)?)|$)"
)
CREATE_DESCRIPTION_PATTERN = re.compile(
    r"描述\s*(?:是|为|:|：)\s*(?P<value>.+?)"
    r"(?=(?:\s*[，,。；;]\s*优先级\s*(?:是|为|:|：)?)|$)"
)
CREATE_PRIORITY_PATTERN = re.compile(
    r"(?<![低中高])优先级\s*(?:是|为|:|：)?\s*(?P<value>[^，,。；;\s]+)"
)
TICKET_ID_PATTERNS = (
    re.compile(r"id\s*(?:为|是|:|：)?\s*(?P<ticket_id>\d+)\s*的?工单", re.I),
    re.compile(r"(?P<ticket_id>\d+)\s*号工单"),
    re.compile(r"工单\s*(?:id)?\s*(?:为|是|:|：)?\s*(?P<ticket_id>\d+)", re.I),
)
UPDATE_STATUS_PATTERNS = (
    re.compile(r"(?:改成|改为|修改为|更新为)\s*(?P<status>[^，,。；;\s]+)"),
    re.compile(r"状态\s*(?:改为|改成|更新为|修改为|为|是|:|：)\s*(?P<status>[^，,。；;\s]+)"),
)
SAVE_AI_REPLY_CONTENT_PATTERNS = (
    re.compile(r"(?:内容|建议|回复)\s*(?:是|为|:|：)\s*(?P<content>.+)$"),
    re.compile(r"(?:保存|采纳).*(?:回复建议|AI回复建议|ai回复建议|建议)\s*[：:]\s*(?P<content>.+)$"),
)


class IntentRecognizer:
    def recognize(self, message: str) -> IntentResult:
        normalized_message = message.strip()
        lowered_message = normalized_message.lower()

        if self._is_confirm(lowered_message):
            return self._result(
                IntentType.CONFIRM,
                normalized_message,
                confidence=1.0,
            )

        if self._is_cancel(lowered_message):
            return self._result(
                IntentType.CANCEL,
                normalized_message,
                confidence=1.0,
            )

        if self._is_save_ai_reply(lowered_message):
            return self._recognize_save_ai_reply(normalized_message)

        if self._is_ticket_summary(lowered_message):
            return self._recognize_ticket_detail_ai_intent(
                IntentType.TICKET_SUMMARY,
                normalized_message,
            )

        if self._is_priority_suggestion(lowered_message):
            return self._recognize_ticket_detail_ai_intent(
                IntentType.PRIORITY_SUGGESTION,
                normalized_message,
            )

        if self._is_category_suggestion(lowered_message):
            return self._recognize_ticket_detail_ai_intent(
                IntentType.CATEGORY_SUGGESTION,
                normalized_message,
            )

        if self._is_similar_ticket_search(lowered_message):
            return self._recognize_ticket_detail_ai_intent(
                IntentType.SIMILAR_TICKET_SEARCH,
                normalized_message,
            )

        if self._is_sla_risk_check(lowered_message):
            return self._recognize_ticket_detail_ai_intent(
                IntentType.SLA_RISK_CHECK,
                normalized_message,
            )

        if self._is_reply_suggestion(lowered_message):
            return self._recognize_reply_suggestion(normalized_message)

        if self._is_create_ticket(lowered_message):
            return self._recognize_create_ticket(normalized_message)

        if self._is_update_ticket_status(lowered_message):
            return self._recognize_update_ticket_status(normalized_message)

        if self._is_query_ticket(lowered_message):
            return self._recognize_query_ticket(normalized_message)

        return self._result(IntentType.UNKNOWN, normalized_message)

    def _is_confirm(self, lowered_message: str) -> bool:
        return lowered_message in {trigger.lower() for trigger in CONFIRM_TRIGGERS}

    def _is_cancel(self, lowered_message: str) -> bool:
        return lowered_message in {trigger.lower() for trigger in CANCEL_TRIGGERS}

    def _is_query_ticket(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False

        if any(trigger.lower() in lowered_message for trigger in QUERY_TICKET_TRIGGERS):
            return True

        return any(
            [
                extract_status_from_text(lowered_message),
                extract_priority_from_text(lowered_message),
                self._extract_search_keyword(lowered_message),
            ]
        )

    def _is_create_ticket(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False
        return any(trigger.lower() in lowered_message for trigger in CREATE_TICKET_TRIGGERS)

    def _is_update_ticket_status(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False

        has_query_intent = any(
            trigger in lowered_message for trigger in UPDATE_TICKET_QUERY_TRIGGERS
        )
        has_update_verb = any(
            trigger in lowered_message
            for trigger in ("改成", "改为", "修改", "更新", "关闭", "完成", "处理完")
        )
        if has_query_intent and not has_update_verb:
            return False

        return any(
            trigger in lowered_message for trigger in UPDATE_TICKET_ACTION_TRIGGERS
        )

    def _is_reply_suggestion(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False
        return any(
            trigger.lower() in lowered_message
            for trigger in REPLY_SUGGESTION_TRIGGERS
        )

    def _is_ticket_summary(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False
        return any(
            trigger.lower() in lowered_message
            for trigger in TICKET_SUMMARY_TRIGGERS
        )

    def _is_priority_suggestion(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message and "优先级" not in lowered_message:
            return False
        return any(
            trigger.lower() in lowered_message
            for trigger in PRIORITY_SUGGESTION_TRIGGERS
        )

    def _is_category_suggestion(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message and "分类" not in lowered_message:
            return False
        return any(
            trigger.lower() in lowered_message
            for trigger in CATEGORY_SUGGESTION_TRIGGERS
        )

    def _is_similar_ticket_search(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False
        return any(
            trigger.lower() in lowered_message
            for trigger in SIMILAR_TICKET_TRIGGERS
        )

    def _is_sla_risk_check(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message and "sla" not in lowered_message:
            return False
        return any(trigger.lower() in lowered_message for trigger in SLA_RISK_TRIGGERS)

    def _is_save_ai_reply(self, lowered_message: str) -> bool:
        if "工单" not in lowered_message:
            return False
        has_save_action = "保存" in lowered_message or "采纳" in lowered_message
        has_reply_suggestion_target = (
            "回复建议" in lowered_message
            or "ai 回复" in lowered_message
            or "ai回复" in lowered_message
        )
        if has_save_action and has_reply_suggestion_target:
            return True
        return any(
            trigger.lower() in lowered_message
            for trigger in SAVE_AI_REPLY_TRIGGERS
        )

    def _recognize_query_ticket(self, message: str) -> IntentResult:
        return self._result(
            IntentType.QUERY_TICKET,
            message,
            priority=extract_priority_from_text(message),
            target_status=extract_status_from_text(message),
            keyword=self._extract_search_keyword(message),
            confidence=0.8,
        )

    def _recognize_create_ticket(self, message: str) -> IntentResult:
        title = self._extract_create_field(message, CREATE_TITLE_PATTERN)
        description = self._extract_create_field(message, CREATE_DESCRIPTION_PATTERN)
        priority = self._extract_create_priority(message)
        missing_fields = [
            field
            for field, value in (
                ("title", title),
                ("description", description),
                ("priority", priority),
            )
            if not value
        ]

        return self._result(
            IntentType.CREATE_TICKET,
            message,
            title=title,
            description=description,
            priority=priority,
            missing_fields=missing_fields,
            confidence=0.85 if not missing_fields else 0.65,
        )

    def _recognize_update_ticket_status(self, message: str) -> IntentResult:
        ticket_id = self._extract_ticket_id(message)
        target_status = self._extract_update_target_status(message)
        missing_fields = []
        if ticket_id is None:
            missing_fields.append("ticket_id")
        if target_status is None:
            missing_fields.append("target_status")

        return self._result(
            IntentType.UPDATE_TICKET_STATUS,
            message,
            ticket_id=ticket_id,
            target_status=target_status,
            missing_fields=missing_fields,
            confidence=0.85 if not missing_fields else 0.65,
        )

    def _recognize_reply_suggestion(self, message: str) -> IntentResult:
        ticket_id = self._extract_ticket_id(message)
        missing_fields = [] if ticket_id is not None else ["ticket_id"]
        return self._result(
            IntentType.REPLY_SUGGESTION,
            message,
            ticket_id=ticket_id,
            missing_fields=missing_fields,
            confidence=0.85 if not missing_fields else 0.65,
        )

    def _recognize_ticket_detail_ai_intent(
        self,
        intent: IntentType,
        message: str,
    ) -> IntentResult:
        ticket_id = self._extract_ticket_id(message)
        missing_fields = [] if ticket_id is not None else ["ticket_id"]
        return self._result(
            intent,
            message,
            ticket_id=ticket_id,
            missing_fields=missing_fields,
            confidence=0.85 if not missing_fields else 0.65,
        )

    def _recognize_save_ai_reply(self, message: str) -> IntentResult:
        ticket_id = self._extract_ticket_id(message)
        reply_content = self._extract_save_ai_reply_content(message)
        missing_fields = []
        if ticket_id is None:
            missing_fields.append("ticket_id")
        if not reply_content:
            missing_fields.append("reply_content")

        return self._result(
            IntentType.SAVE_AI_REPLY,
            message,
            ticket_id=ticket_id,
            reply_content=reply_content,
            missing_fields=missing_fields,
            confidence=0.85 if not missing_fields else 0.65,
        )

    def _extract_search_keyword(self, message: str) -> str | None:
        normalized_message = message.strip()
        for pattern in KEYWORD_PATTERNS:
            match = pattern.search(normalized_message)
            if not match:
                continue
            keyword = match.group("keyword").strip(" ，,。！？?；;：:")
            if keyword:
                return keyword
        return None

    def _extract_create_field(
        self,
        message: str,
        pattern: re.Pattern[str],
    ) -> str | None:
        match = pattern.search(message.strip())
        if not match:
            return None
        value = match.group("value").strip(" ，,。！？?；;：:")
        return value or None

    def _extract_create_priority(self, message: str) -> str | None:
        explicit_match = CREATE_PRIORITY_PATTERN.search(message.strip())
        if explicit_match:
            priority_text = explicit_match.group("value").strip(" ，,。！？?；;：:")
            return normalize_priority_text(priority_text)

        prefix = re.split(r"标题\s*(?:是|为|:|：)", message.strip(), maxsplit=1)[0]
        return extract_priority_from_text(prefix)

    def _extract_ticket_id(self, message: str) -> int | None:
        normalized_message = message.strip()
        for pattern in TICKET_ID_PATTERNS:
            match = pattern.search(normalized_message)
            if match:
                return int(match.group("ticket_id"))
        return None

    def _extract_update_target_status(self, message: str) -> str | None:
        normalized_message = message.strip()
        for pattern in UPDATE_STATUS_PATTERNS:
            match = pattern.search(normalized_message)
            if not match:
                continue
            status_text = match.group("status").strip(" ，,。！？?；;：:")
            return normalize_status(status_text)

        if "关闭" in normalized_message or "已关闭" in normalized_message:
            return "CLOSED"
        if (
            "处理完了" in normalized_message
            or "处理完" in normalized_message
            or "已完成" in normalized_message
            or "完成" in normalized_message
        ):
            return "CLOSED"
        if "处理中" in normalized_message:
            return "PROCESSING"

        return None

    def _extract_save_ai_reply_content(self, message: str) -> str | None:
        normalized_message = message.strip()
        for pattern in SAVE_AI_REPLY_CONTENT_PATTERNS:
            match = pattern.search(normalized_message)
            if not match:
                continue
            content = match.group("content").strip(" ，,。！？?；;：:")
            if content:
                return content
        return None

    def _result(
        self,
        intent: IntentType,
        raw_message: str,
        *,
        ticket_id: int | None = None,
        title: str | None = None,
        description: str | None = None,
        priority: str | None = None,
        target_status: str | None = None,
        keyword: str | None = None,
        reply_content: str | None = None,
        confidence: float = 0.0,
        missing_fields: list[str] | None = None,
    ) -> IntentResult:
        return IntentResult(
            intent=intent,
            ticket_id=ticket_id,
            title=title,
            description=description,
            priority=priority,
            target_status=target_status,
            keyword=keyword,
            reply_content=reply_content,
            confidence=confidence,
            missing_fields=missing_fields or [],
            raw_message=raw_message,
        )


class LLMIntentRecognizer(IntentRecognizer):
    """预留给后续 LLM 结构化意图识别的替换点。"""

    pass
