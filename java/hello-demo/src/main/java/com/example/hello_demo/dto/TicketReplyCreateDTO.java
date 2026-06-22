package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增工单回复请求对象。
 * 回复人和回复类型由后端根据当前登录用户自动设置。
 */
public class TicketReplyCreateDTO {

    @NotBlank(message = "content不能为空")
    private String content;

    public TicketReplyCreateDTO() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
