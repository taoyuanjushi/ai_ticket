package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request for creating a pending action to save an AI reply suggestion.
 */
public class AiReplyPendingRequest {

    @NotBlank(message = "conversationId cannot be blank")
    private String conversationId;

    @Size(max = 2000, message = "suggestion cannot exceed 2000 characters")
    private String suggestion;

    @Size(max = 2000, message = "originalSuggestion cannot exceed 2000 characters")
    private String originalSuggestion;

    private Double confidence;

    @Size(max = 500, message = "reason cannot exceed 500 characters")
    private String reason;

    private List<String> riskFlags;

    /**
     * Legacy field kept for compatibility with older frontend/tests.
     */
    @Size(max = 2000, message = "content cannot exceed 2000 characters")
    private String content;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getOriginalSuggestion() {
        return originalSuggestion;
    }

    public void setOriginalSuggestion(String originalSuggestion) {
        this.originalSuggestion = originalSuggestion;
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

    public List<String> getRiskFlags() {
        return riskFlags;
    }

    public void setRiskFlags(List<String> riskFlags) {
        this.riskFlags = riskFlags;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
