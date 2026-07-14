USE smart_nursing;
SET NAMES utf8mb4;

SET @has_feedback_priority_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'elder_health_feedback'
    AND index_name = 'idx_health_feedback_elder_severity_created'
);
SET @sql := IF(@has_feedback_priority_index = 0,
  'ALTER TABLE elder_health_feedback ADD KEY idx_health_feedback_elder_severity_created (elder_id, severity, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
