package com.example.hello_demo.vo;

import java.time.LocalDateTime;

public class OperationLogVO {

    private Long id;
    private Long ticketId;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String detail;
    private LocalDateTime createdAt;

    public OperationLogVO() {
    }

    public OperationLogVO(Long id, Long ticketId, Long operatorId, String operatorName, String action, String detail, LocalDateTime createdAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
