CREATE TABLE IF NOT EXISTS ai_pending_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI待确认动作主键',
    user_id BIGINT NOT NULL COMMENT '发起用户ID，来自JWT',
    conversation_id VARCHAR(128) NOT NULL COMMENT '会话ID',
    action_type VARCHAR(64) NOT NULL COMMENT '动作类型：CREATE_TICKET、UPDATE_TICKET_STATUS、SAVE_AI_REPLY、APPLY_AI_CATEGORY',
    payload_json TEXT NOT NULL COMMENT '业务参数JSON，不保存token',
    status VARCHAR(32) NOT NULL COMMENT '状态：PENDING、CONFIRMED、CANCELLED、EXPIRED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    confirmed_at DATETIME NULL COMMENT '确认时间',
    cancelled_at DATETIME NULL COMMENT '取消时间',
    INDEX idx_ai_pending_user_conversation_status (user_id, conversation_id, status),
    INDEX idx_ai_pending_status_created_at (status, created_at)
) COMMENT='AI待确认动作表';
