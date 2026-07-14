USE smart_nursing;
SET NAMES utf8mb4;

SET @has_review_task_suggestion_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'health_info_review_task'
    AND column_name = 'suggestion_id'
);
SET @sql := IF(@has_review_task_suggestion_id = 0,
  'ALTER TABLE health_info_review_task ADD COLUMN suggestion_id VARCHAR(32) NULL COMMENT ''健康变更建议ID'' AFTER review_task_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_task_task_type := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'health_info_review_task'
    AND column_name = 'task_type'
);
SET @sql := IF(@has_review_task_task_type = 0,
  'ALTER TABLE health_info_review_task ADD COLUMN task_type VARCHAR(32) NOT NULL DEFAULT ''HEALTH_UPDATE'' COMMENT ''任务类型'' AFTER suggestion_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_health_review_status_check := (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'health_info_review_task'
    AND constraint_name = 'ck_health_review_status'
    AND constraint_type = 'CHECK'
);
SET @sql := IF(@has_health_review_status_check > 0,
  'ALTER TABLE health_info_review_task DROP CHECK ck_health_review_status',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE health_info_review_task
  ADD CONSTRAINT ck_health_review_status CHECK (review_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'));

SET @has_health_review_source_check := (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'health_info_review_task'
    AND constraint_name = 'ck_health_review_source_type'
    AND constraint_type = 'CHECK'
);
SET @sql := IF(@has_health_review_source_check > 0,
  'ALTER TABLE health_info_review_task DROP CHECK ck_health_review_source_type',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE health_info_review_task
  ADD CONSTRAINT ck_health_review_source_type CHECK (source_type IN ('REPORT_ACK','SERVICE_RECORD','SERVICE_REPORT','MANUAL','MEDICAL_FILE','SUGGESTION'));
