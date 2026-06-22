-- 如果已有 operation_log 表缺少 operation_type 索引，可以执行本升级 SQL。
-- 该脚本只新增索引，不删除表，也不清空已有数据。
SET @index_count := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operation_log'
      AND index_name = 'idx_operation_log_operation_type'
);

SET @sql := IF(
    @index_count = 0,
    'ALTER TABLE operation_log ADD INDEX idx_operation_log_operation_type (operation_type)',
    'SELECT ''idx_operation_log_operation_type already exists'''
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
