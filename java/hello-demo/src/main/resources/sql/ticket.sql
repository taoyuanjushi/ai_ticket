CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单主键',
    title VARCHAR(100) NOT NULL COMMENT '工单标题',
    content TEXT NOT NULL COMMENT '工单内容',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' COMMENT '工单状态：OPEN-待处理，PROCESSING-处理中，CLOSED-已关闭',
    priority VARCHAR(30) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW-低，MEDIUM-中，HIGH-高',
    category VARCHAR(50) DEFAULT 'OTHER' COMMENT '工单分类',
    user_id BIGINT NOT NULL COMMENT '提交工单的用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_user_id (user_id),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_priority (priority),
    INDEX idx_ticket_category (category)
) COMMENT='工单表';

INSERT INTO ticket (title, content, status, priority, category, user_id)
VALUES
('登录失败', '用户反馈登录系统时提示账号不存在', 'OPEN', 'HIGH', 'ACCOUNT', 1),
('页面加载慢', '用户反馈后台首页加载速度较慢', 'PROCESSING', 'MEDIUM', 'SYSTEM', 2);
