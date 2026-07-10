USE smart_nursing;
SET NAMES utf8mb4;

SET @idx_order_status_start_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'nursing_order'
    AND index_name = 'idx_nursing_order_status_start'
);
SET @idx_order_status_start_sql = IF(
  @idx_order_status_start_exists = 0,
  'CREATE INDEX idx_nursing_order_status_start ON nursing_order (order_status, scheduled_start_at)',
  'DO 0'
);
PREPARE idx_order_status_start_stmt FROM @idx_order_status_start_sql;
EXECUTE idx_order_status_start_stmt;
DEALLOCATE PREPARE idx_order_status_start_stmt;

SET @idx_order_family_status_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'nursing_order'
    AND index_name = 'idx_nursing_order_family_status'
);
SET @idx_order_family_status_sql = IF(
  @idx_order_family_status_exists = 0,
  'CREATE INDEX idx_nursing_order_family_status ON nursing_order (family_id, order_status)',
  'DO 0'
);
PREPARE idx_order_family_status_stmt FROM @idx_order_family_status_sql;
EXECUTE idx_order_family_status_stmt;
DEALLOCATE PREPARE idx_order_family_status_stmt;
