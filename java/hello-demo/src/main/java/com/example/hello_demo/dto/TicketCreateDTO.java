package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增工单请求对象。
 * 创建工单时 userId 由后端从 JWT 中获取，不接收前端传入的 userId。
 */
public class TicketCreateDTO {

    @NotBlank(message = "title不能为空")
    private String title;

    @NotBlank(message = "content不能为空")
    private String content;

    private String priority;

    private String category;

    public TicketCreateDTO() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
