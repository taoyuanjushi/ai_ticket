from app.schemas.intent_schema import IntentType
from app.services.intent_recognizer import IntentRecognizer


def test_query_ticket_intent() -> None:
    result = IntentRecognizer().recognize("查询我的工单")

    assert result.intent == IntentType.QUERY_TICKET
    assert result.missing_fields == []


def test_create_ticket_intent() -> None:
    result = IntentRecognizer().recognize(
        "帮我创建一个工单，标题是登录失败，描述是用户无法登录，优先级高"
    )

    assert result.intent == IntentType.CREATE_TICKET
    assert result.title == "登录失败"
    assert result.description == "用户无法登录"
    assert result.priority == "HIGH"
    assert result.missing_fields == []


def test_update_ticket_status_intent() -> None:
    result = IntentRecognizer().recognize("把 1 号工单改成处理中")

    assert result.intent == IntentType.UPDATE_TICKET_STATUS
    assert result.ticket_id == 1
    assert result.target_status == "PROCESSING"
    assert result.missing_fields == []


def test_reply_suggestion_intent() -> None:
    result = IntentRecognizer().recognize("帮我给 1 号工单生成回复建议")

    assert result.intent == IntentType.REPLY_SUGGESTION
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_save_ai_reply_intent() -> None:
    result = IntentRecognizer().recognize(
        "保存 1 号工单的 AI 回复建议，内容是请用户补充错误截图。"
    )

    assert result.intent == IntentType.SAVE_AI_REPLY
    assert result.ticket_id == 1
    assert result.reply_content == "请用户补充错误截图"
    assert result.missing_fields == []


def test_ticket_summary_intent() -> None:
    result = IntentRecognizer().recognize("总结一下 1 号工单")

    assert result.intent == IntentType.TICKET_SUMMARY
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_priority_suggestion_intent() -> None:
    result = IntentRecognizer().recognize("判断 1 号工单优先级是否合理")

    assert result.intent == IntentType.PRIORITY_SUGGESTION
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_category_suggestion_intent() -> None:
    result = IntentRecognizer().recognize("给 1 号工单一个分类建议")

    assert result.intent == IntentType.CATEGORY_SUGGESTION
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_sla_risk_intent() -> None:
    result = IntentRecognizer().recognize("检查 1 号工单 SLA 风险")

    assert result.intent == IntentType.SLA_RISK_CHECK
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_similar_ticket_search_intent() -> None:
    result = IntentRecognizer().recognize("1 号工单有没有类似工单")

    assert result.intent == IntentType.SIMILAR_TICKET_SEARCH
    assert result.ticket_id == 1
    assert result.missing_fields == []


def test_priority_suggestion_missing_ticket_id() -> None:
    result = IntentRecognizer().recognize("这个工单优先级应该是多少")

    assert result.intent == IntentType.PRIORITY_SUGGESTION
    assert result.missing_fields == ["ticket_id"]


def test_category_suggestion_missing_ticket_id() -> None:
    result = IntentRecognizer().recognize("这个工单属于什么分类")

    assert result.intent == IntentType.CATEGORY_SUGGESTION
    assert result.missing_fields == ["ticket_id"]


def test_sla_risk_missing_ticket_id() -> None:
    result = IntentRecognizer().recognize("这个工单有 SLA 风险吗")

    assert result.intent == IntentType.SLA_RISK_CHECK
    assert result.missing_fields == ["ticket_id"]


def test_confirm_intent() -> None:
    result = IntentRecognizer().recognize("没问题")

    assert result.intent == IntentType.CONFIRM


def test_cancel_intent() -> None:
    result = IntentRecognizer().recognize("先不改了")

    assert result.intent == IntentType.CANCEL


def test_completed_text_maps_to_closed() -> None:
    result = IntentRecognizer().recognize("把 2 号工单改成已完成")

    assert result.intent == IntentType.UPDATE_TICKET_STATUS
    assert result.target_status == "CLOSED"
    assert result.target_status != "DONE"


def test_create_ticket_missing_fields() -> None:
    result = IntentRecognizer().recognize("创建工单")

    assert result.intent == IntentType.CREATE_TICKET
    assert result.missing_fields == ["title", "description", "priority"]


def test_update_ticket_status_missing_fields() -> None:
    result = IntentRecognizer().recognize("修改工单状态")

    assert result.intent == IntentType.UPDATE_TICKET_STATUS
    assert result.missing_fields == ["ticket_id", "target_status"]


def test_reply_suggestion_missing_ticket_id() -> None:
    result = IntentRecognizer().recognize("这个工单怎么回复")

    assert result.intent == IntentType.REPLY_SUGGESTION
    assert result.missing_fields == ["ticket_id"]


def test_unknown_intent() -> None:
    result = IntentRecognizer().recognize("你好")

    assert result.intent == IntentType.UNKNOWN
    assert result.confidence == 0.0
