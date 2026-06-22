package com.example.hello_demo.dto;

/**
 * 工单分页查询请求参数。
 * GET /tickets 会从 URL query string 中自动绑定这些字段。
 */
public class TicketQueryRequest {

    private Long page;
    private Long size;
    private String status;
    private String priority;
    private String category;
    private String keyword;

    public TicketQueryRequest() {
    }

    public Long getPage() {
        return page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
