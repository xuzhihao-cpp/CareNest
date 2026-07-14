USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO sys_permission(permission_id,permission_code,permission_name,permission_group,enabled)
VALUES ('perm_health_archive_review','HEALTH_ARCHIVE_REVIEW','健康档案审核','health',1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),enabled=1;

INSERT IGNORE INTO role_permission(role_id,permission_id,sort)
SELECT role_id,'perm_health_archive_review',90 FROM sys_role WHERE role_code IN ('ADMIN','CUSTOMER_SERVICE');

ALTER TABLE health_info_review_task MODIFY old_value TEXT NULL, MODIFY new_value TEXT NOT NULL;

SET @has_suggestion_id := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='health_info_review_task' AND column_name='suggestion_id');
SET @sql := IF(@has_suggestion_id=0,'ALTER TABLE health_info_review_task ADD COLUMN suggestion_id VARCHAR(32) NULL AFTER review_task_id','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_task_type := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='health_info_review_task' AND column_name='task_type');
SET @sql := IF(@has_task_type=0,'ALTER TABLE health_info_review_task ADD COLUMN task_type VARCHAR(32) NOT NULL DEFAULT ''HEALTH_UPDATE'' AFTER suggestion_id','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_source_check := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='health_info_review_task' AND constraint_name='ck_health_review_source_type' AND constraint_type='CHECK');
SET @sql := IF(@has_source_check>0,'ALTER TABLE health_info_review_task DROP CHECK ck_health_review_source_type','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE health_info_review_task ADD CONSTRAINT ck_health_review_source_type CHECK (source_type IN ('REPORT_ACK','SERVICE_RECORD','SERVICE_REPORT','MANUAL','MEDICAL_FILE','SUGGESTION'));

SET @has_dedupe := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='health_update_suggestion' AND column_name='pending_dedupe_key');
SET @sql := IF(@has_dedupe=0,'ALTER TABLE health_update_suggestion ADD COLUMN pending_dedupe_key CHAR(64) GENERATED ALWAYS AS (IF(suggestion_status = ''PENDING'', SHA2(CONCAT_WS(''|'', order_id, source_type, source_id, field_name, new_value), 256), NULL)) STORED','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_dedupe_index := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='health_update_suggestion' AND index_name='uk_health_suggestion_pending_dedupe');
SET @sql := IF(@has_dedupe_index=0,'ALTER TABLE health_update_suggestion ADD UNIQUE KEY uk_health_suggestion_pending_dedupe (pending_dedupe_key)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
