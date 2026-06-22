package com.example.hello_demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java 转发给 Python /agent/chat 的请求对象。
 * 字段名保持 Python FastAPI 入参格式。
 */
public class PythonAgentChatRequestDTO {

    private String message;

    @JsonProperty("conversation_id")
    private String conversation_id;

    @JsonProperty("user_id")
    private Long user_id;

    @JsonProperty("auth_token")
    private String auth_token;

    public PythonAgentChatRequestDTO() {
    }

    public PythonAgentChatRequestDTO(String message, String conversationId, Long userId, String authToken) {
        this.message = message;
        this.conversation_id = conversationId;
        this.user_id = userId;
        this.auth_token = authToken;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversation_id() {
        return conversation_id;
    }

    public void setConversation_id(String conversation_id) {
        this.conversation_id = conversation_id;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public String getAuth_token() {
        return auth_token;
    }

    public void setAuth_token(String auth_token) {
        this.auth_token = auth_token;
    }
}
