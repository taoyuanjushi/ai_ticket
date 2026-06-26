package com.example.hello_demo.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改工单分类请求对象。
 */
public class TicketCategoryUpdateRequest {

    @Size(max = 64, message = "工单分类长度不能超过64")
    private String category;

    public TicketCategoryUpdateRequest() {
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
