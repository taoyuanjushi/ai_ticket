package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 确认或取消 AI 待确认动作请求对象。
 */
public class AiPendingActionConversationRequest {

    @NotBlank(message = "conversationId不能为空")
    private String conversationId;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
