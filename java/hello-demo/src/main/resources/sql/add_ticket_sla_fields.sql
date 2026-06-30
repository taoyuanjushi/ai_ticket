USE springboot_demo;

-- 手工迁移：为已有 ticket 表增加 SLA 字段。
-- 执行前请先确认字段和索引不存在，避免重复执行报错。

ALTER TABLE ticket
ADD COLUMN response_due_at DATETIME NULL COMMENT '首次响应截止时间',
ADD COLUMN resolve_due_at DATETIME NULL COMMENT '解决截止时间',
ADD COLUMN closed_at DATETIME NULL COMMENT '工单关闭时间';

CREATE INDEX idx_ticket_resolve_due_at ON ticket(resolve_due_at);
CREATE INDEX idx_ticket_closed_at ON ticket(closed_at);
