package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 对话请求 DTO。
 */
public class AiChatRequestDTO {

    @NotBlank(message = "message不能为空")
    private String message;

    private String conversationId;

    public AiChatRequestDTO() {
    }

    public AiChatRequestDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
