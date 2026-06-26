package com.example.hello_demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 返回给前端的 AI 待确认卡片数据。
 */
public class AiPendingConfirmationResponse {

    private String type;
    private String message;
    private Map<String, Object> data;

    @JsonProperty("risk_flags")
    private List<String> riskFlags = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public List<String> getRiskFlags() {
        return riskFlags;
    }

    public void setRiskFlags(List<String> riskFlags) {
        this.riskFlags = riskFlags == null ? new ArrayList<>() : riskFlags;
    }
}
