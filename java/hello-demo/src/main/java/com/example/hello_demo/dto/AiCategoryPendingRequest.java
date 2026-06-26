package com.example.hello_demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建采纳 AI 分类待确认动作请求对象。
 */
public class AiCategoryPendingRequest {

    @NotBlank(message = "conversationId不能为空")
    private String conversationId;

    @NotBlank(message = "category不能为空")
    @Size(max = 64, message = "工单分类长度不能超过64")
    private String category;

    @DecimalMin(value = "0.0", message = "confidence不能小于0")
    @DecimalMax(value = "1.0", message = "confidence不能大于1")
    private Double confidence;

    @Size(max = 500, message = "reason长度不能超过500")
    private String reason;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
