CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '操作日志主键',
    user_id BIGINT COMMENT '操作人用户ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型，例如 CREATE_TICKET、REPLY_TICKET、UPDATE_TICKET_STATUS、DELETE_TICKET、LOGIN_SUCCESS、LOGIN_FAILED',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型，例如 AUTH、USER、TICKET、TICKET_REPLY',
    business_id BIGINT COMMENT '业务数据ID',
    content VARCHAR(500) NOT NULL COMMENT '操作内容描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_operation_log_user_id (user_id),
    INDEX idx_operation_log_business (business_type, business_id),
    INDEX idx_operation_log_operation_type (operation_type),
    INDEX idx_operation_log_created_at (created_at)
) COMMENT='操作日志表';
