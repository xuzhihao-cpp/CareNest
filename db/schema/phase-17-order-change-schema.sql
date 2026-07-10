USE smart_nursing;
SET NAMES utf8mb4;

SET @idx_order_status_log_order_created_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_status_log'
    AND index_name = 'idx_order_status_log_order_created'
);
SET @idx_order_status_log_order_created_sql = IF(
  @idx_order_status_log_order_created_exists = 0,
  'CREATE INDEX idx_order_status_log_order_created ON order_status_log (order_id, created_at)',
  'DO 0'
);
PREPARE idx_order_status_log_order_created_stmt FROM @idx_order_status_log_order_created_sql;
EXECUTE idx_order_status_log_order_created_stmt;
DEALLOCATE PREPARE idx_order_status_log_order_created_stmt;
