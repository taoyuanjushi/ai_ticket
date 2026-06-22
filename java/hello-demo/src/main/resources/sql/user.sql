CREATE DATABASE IF NOT EXISTS springboot_demo
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE springboot_demo;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(100) NOT NULL COMMENT '加密后的密码',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    age INT COMMENT '年龄',
    email VARCHAR(100) COMMENT '邮箱',
    role VARCHAR(30) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户，STAFF-客服，ADMIN-管理员',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
);

INSERT IGNORE INTO user (username, password, name, age, email, role) VALUES
('tom', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiwvQvBhv81Zr7.cqTwHjqOTgiqKx1e', 'Tom', 20, 'tom@example.com', 'USER'),
('jack', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiwvQvBhv81Zr7.cqTwHjqOTgiqKx1e', 'Jack', 22, 'jack@example.com', 'USER');
