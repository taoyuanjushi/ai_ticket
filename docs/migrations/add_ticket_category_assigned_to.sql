-- Ticket model upgrade: category + assigned_to.
-- Run this manually for an existing database. Docker MySQL volumes that already
-- contain data will not rerun initialization SQL automatically.

USE springboot_demo;

ALTER TABLE ticket
  MODIFY COLUMN category VARCHAR(64) NULL COMMENT '工单分类',
  ADD COLUMN assigned_to BIGINT NULL COMMENT '处理人用户ID';

CREATE INDEX idx_ticket_assigned_to ON ticket(assigned_to);
