package com.example.hello_demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 保存 AI 回复建议请求对象。
 */
public class AiReplyCreateDTO {

    @NotBlank(message = "content不能为空")
    private String content;

    public AiReplyCreateDTO() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
