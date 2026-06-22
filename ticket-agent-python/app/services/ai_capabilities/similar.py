import re

from app.schemas.ticket import TicketDTO, TicketDetailDTO
from app.schemas.ticket_ai_schema import SimilarTicketItem, SimilarTicketSearchResult
from app.services.ai_capabilities.base import TicketAiCapabilityBase


class SimilarTicketSearchService(TicketAiCapabilityBase):
    DOMAIN_KEYWORDS = (
        "登录",
        "账号",
        "密码",
        "权限",
        "接口",
        "超时",
        "报错",
        "失败",
        "上传",
        "下载",
        "文件",
        "订单",
        "支付",
        "数据库",
        "页面",
        "审批",
    )

    def search(
        self,
        auth_token: str | None,
        ticket_id: int,
        limit: int = 5,
    ) -> SimilarTicketSearchResult:
        ticket_detail = self.get_ticket_detail(auth_token=auth_token, ticket_id=ticket_id)
        risk_flags = self.base_risk_flags(ticket_detail)
        keywords = self.extract_keywords(ticket_detail)
        if not keywords:
            risk_flags = self.merge_risk_flags(risk_flags, ["信息不足"])
            return SimilarTicketSearchResult(similar_tickets=[], risk_flags=risk_flags)

        similar_tickets: list[SimilarTicketItem] = []
        seen_ids = {ticket_detail.id}
        for keyword in keywords:
            candidates = self.search_tickets(
                auth_token=auth_token,
                query={"keyword": keyword, "page": 1, "size": 10},
            )
            for candidate in candidates:
                if candidate.id in seen_ids:
                    continue
                similar_tickets.append(
                    self._to_similar_item(candidate, keyword)
                )
                seen_ids.add(candidate.id)
                if len(similar_tickets) >= limit:
                    return SimilarTicketSearchResult(
                        similar_tickets=similar_tickets,
                        risk_flags=risk_flags,
                    )

        return SimilarTicketSearchResult(
            similar_tickets=similar_tickets,
            risk_flags=risk_flags,
        )

    def extract_keywords(self, ticket_detail: TicketDetailDTO) -> list[str]:
        text = self.text_for_analysis(ticket_detail)
        keywords = [keyword for keyword in self.DOMAIN_KEYWORDS if keyword in text]
        if keywords:
            return keywords[:3]

        normalized_title = re.sub(r"[\s，,。；;：:！？?]+", "", ticket_detail.title or "")
        if len(normalized_title) >= 2:
            return [normalized_title[:12]]
        return []

    def _to_similar_item(
        self,
        ticket: TicketDTO,
        keyword: str,
    ) -> SimilarTicketItem:
        return SimilarTicketItem(
            id=ticket.id,
            title=ticket.title,
            status=ticket.status,
            similarity_reason=f"都涉及“{keyword}”相关问题。",
        )
