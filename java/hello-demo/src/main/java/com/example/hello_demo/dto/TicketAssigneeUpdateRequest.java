package com.example.hello_demo.dto;

/**
 * 修改工单处理人请求对象。
 */
public class TicketAssigneeUpdateRequest {

    private Long assignedTo;

    public TicketAssigneeUpdateRequest() {
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }
}
