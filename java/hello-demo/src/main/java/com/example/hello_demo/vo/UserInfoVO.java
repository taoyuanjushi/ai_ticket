package com.example.hello_demo.vo;

/**
 * 用户详情返回对象。
 * 用于返回安全的用户信息，不包含 password。
 */
public class UserInfoVO {

    private Long id;
    private String username;
    private String name;
    private Integer age;
    private String email;
    private String role;

    public UserInfoVO() {
    }

    public UserInfoVO(Long id, String username, String name, Integer age, String email, String role) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.age = age;
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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
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
