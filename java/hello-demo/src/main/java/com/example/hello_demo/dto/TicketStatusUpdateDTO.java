package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改工单状态请求对象。
 * 只接收 status 字段，不直接使用 Ticket 实体接收请求。
 */
public class TicketStatusUpdateDTO {

    @NotBlank(message = "status不能为空")
    private String status;

    public TicketStatusUpdateDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
