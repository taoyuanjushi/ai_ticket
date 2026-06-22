package com.example.hello_demo.vo;

/**
 * 当前登录用户返回对象。
 * 用于 /auth/me 和注册成功响应，避免把 password 返回给前端。
 */
public class CurrentUserVO {

    private Long id;
    private String username;
    private String name;
    private String email;
    private String role;

    public CurrentUserVO() {
    }

    public CurrentUserVO(Long id, String username, String name, String email, String role) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
