USE springboot_demo;

-- =========================================================
-- 安全提醒
-- =========================================================
-- 本文件用于已有数据库的结构升级参考，不包含 DROP TABLE、TRUNCATE 或清空数据语句。
-- 执行前请先用 SHOW COLUMNS / SHOW INDEX 确认字段或索引是否已存在，避免重复执行报错。
-- 如果线上已有数据，执行 NOT NULL 变更前请先确认旧数据是否满足条件。

-- =========================================================
-- user 表字段检查
-- =========================================================
-- 当前标准字段：
-- id, username, password, name, age, email, role, created_at, updated_at
-- 如果旧表缺少 created_at / updated_at，可按需执行下面语句。
-- ALTER TABLE user
-- ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
-- ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 如果旧表缺少登录认证字段，请优先参考 user_auth_update.sql。

-- =========================================================
-- ticket 表字段和索引
-- =========================================================
-- 登录 JWT 接入后，ticket.user_id 应由后端从当前登录用户中设置，建议数据库层设置为 NOT NULL。
-- 执行前请先确认是否存在 user_id 为空的历史数据。
-- SELECT COUNT(*) FROM ticket WHERE user_id IS NULL;
-- ALTER TABLE ticket
-- MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '提交工单的用户ID';

-- 执行前请确认索引是否已存在，避免重复创建报错。
CREATE INDEX idx_ticket_user_id ON ticket(user_id);
CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_priority ON ticket(priority);
CREATE INDEX idx_ticket_category ON ticket(category);

-- =========================================================
-- ticket_reply 表字段和索引
-- =========================================================
-- 如果 ticket_reply 表已经存在 updated_at 字段，请不要执行下面语句。
-- ALTER TABLE ticket_reply
-- ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 执行前请确认索引是否已存在，避免重复创建报错。
-- 如果旧表已经有 idx_ticket_reply_ticket_id / idx_ticket_reply_user_id，可以继续沿用旧索引名，不必重复创建。
CREATE INDEX idx_reply_ticket_id ON ticket_reply(ticket_id);
CREATE INDEX idx_reply_user_id ON ticket_reply(user_id);

-- =========================================================
-- operation_log 表
-- =========================================================
-- 操作日志表建表语句见 operation_log.sql。

-- =========================================================
-- ai_pending_action 表
-- =========================================================
-- AI 写操作确认态持久化表，建表语句见 ai_pending_action.sql。
-- payload_json 只保存业务参数，不能保存 token / Authorization。
