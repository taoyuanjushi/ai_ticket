package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 创建 AI 待确认动作请求对象。
 */
public class AiPendingActionCreateRequest {

    @NotBlank(message = "conversationId不能为空")
    private String conversationId;

    @NotBlank(message = "actionType不能为空")
    private String actionType;

    @NotNull(message = "payload不能为空")
    private Map<String, Object> payload;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
