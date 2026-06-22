CREATE TABLE IF NOT EXISTS ticket_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '回复主键',
    ticket_id BIGINT NOT NULL COMMENT '所属工单ID',
    user_id BIGINT NOT NULL COMMENT '回复人用户ID',
    content TEXT NOT NULL COMMENT '回复内容',
    reply_type VARCHAR(30) NOT NULL COMMENT '回复类型：USER-用户回复，STAFF-客服回复，AI-AI回复建议',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_reply_ticket_id (ticket_id),
    INDEX idx_reply_user_id (user_id)
) COMMENT='工单回复表';

INSERT INTO ticket_reply (ticket_id, user_id, content, reply_type)
VALUES
(1, 1, '我已经尝试重置密码，但还是无法登录。', 'USER'),
(1, 2, '请确认账号是否已经注册，必要时联系管理员重置账号。', 'STAFF');
