package com.example.hello_demo.vo;

/**
 * 登录成功返回对象。
 * 返回 Token 和基础身份信息，不返回密码。
 */
public class LoginResponseVO {

    private String token;
    private Long userId;
    private String username;
    private String role;

    public LoginResponseVO() {
    }

    public LoginResponseVO(String token, Long userId, String username, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
