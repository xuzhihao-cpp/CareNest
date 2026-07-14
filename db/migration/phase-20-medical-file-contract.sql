USE smart_nursing;
SET NAMES utf8mb4;

UPDATE file_asset SET audit_status = 'PENDING' WHERE audit_status = 'PENDING_REVIEW';
UPDATE file_asset SET audit_status = 'NEED_MORE' WHERE audit_status = 'NEEDS_SUPPLEMENT';
UPDATE medical_file SET audit_status = 'PENDING' WHERE audit_status = 'PENDING_REVIEW';
UPDATE medical_file SET audit_status = 'NEED_MORE' WHERE audit_status = 'NEEDS_SUPPLEMENT';

SET @has_asset_owner_status_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'file_asset'
    AND index_name = 'idx_file_asset_owner_status'
);
SET @sql := IF(@has_asset_owner_status_index = 0,
  'ALTER TABLE file_asset ADD KEY idx_file_asset_owner_status (uploaded_by, audit_status, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
