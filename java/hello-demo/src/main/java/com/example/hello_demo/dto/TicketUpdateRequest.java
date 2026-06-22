package com.example.hello_demo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * 修改工单请求对象。
 * 只接收允许前端修改的字段，避免请求体中的 id、createdAt 等字段影响数据库数据。
 */
public class TicketUpdateRequest {

    @NotBlank(message = "工单标题不能为空")
    private String title;

    @JsonAlias("description")
    @NotBlank(message = "工单描述不能为空")
    private String content;

    @NotBlank(message = "工单优先级不能为空")
    private String priority;

    @NotBlank(message = "工单状态不能为空")
    private String status;

    private String category;

    public TicketUpdateRequest() {
    }

    public TicketUpdateRequest(String title, String content, String priority, String status, String category) {
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.status = status;
        this.category = category;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
