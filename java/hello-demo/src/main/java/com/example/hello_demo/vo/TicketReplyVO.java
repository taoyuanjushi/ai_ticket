package com.example.hello_demo.vo;

import java.time.LocalDateTime;

public record TicketReplyVO(
        Long id,
        Long ticketId,
        Long userId,
        String authorName,
        String authorRole,
        String content,
        String replyType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
