USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS nurse_profile (
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  real_name VARCHAR(64) DEFAULT NULL COMMENT 'Real name snapshot for qualification workflow',
  id_no_masked VARCHAR(32) DEFAULT NULL COMMENT 'Masked identity number only',
  qualification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Qualification audit status',
  training_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Training summary status',
  can_take_order TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether current summary allows formal order taking',
  profile_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Nurse profile status',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (nurse_id),
  KEY idx_nurse_profile_qualification (qualification_status),
  KEY idx_nurse_profile_training (training_status),
  CONSTRAINT fk_nurse_profile_user FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_profile_qualification_status CHECK (qualification_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE')),
  CONSTRAINT ck_nurse_profile_training_status CHECK (training_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE')),
  CONSTRAINT ck_nurse_profile_status CHECK (profile_status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse profile and admission summary';

CREATE TABLE IF NOT EXISTS nurse_skill_dictionary (
  skill_code VARCHAR(64) NOT NULL COMMENT 'Stable nurse skill code',
  skill_name VARCHAR(128) NOT NULL COMMENT 'Chinese business display name',
  sort INT NOT NULL DEFAULT 0 COMMENT 'Display order',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether the option can be selected',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (skill_code),
  KEY idx_nurse_skill_dictionary_enabled (enabled, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Selectable nurse service skill dictionary';

SET @has_file_asset_owner_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_asset'
    AND index_name = 'idx_file_asset_file_owner'
);
SET @sql := IF(@has_file_asset_owner_idx = 0,
  'ALTER TABLE file_asset ADD INDEX idx_file_asset_file_owner (file_id, uploaded_by)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS nurse_certificate (
  certificate_id VARCHAR(32) NOT NULL COMMENT 'Certificate row ID',
  application_id VARCHAR(32) NOT NULL COMMENT 'Qualification application ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  real_name VARCHAR(64) DEFAULT NULL COMMENT 'Submitted real-name snapshot',
  id_no_masked VARCHAR(32) DEFAULT NULL COMMENT 'Submitted masked identity number',
  certificate_no VARCHAR(64) NOT NULL COMMENT 'Nursing certificate number',
  file_id VARCHAR(32) NOT NULL COMMENT 'Certificate file asset ID',
  service_skill_codes JSON NOT NULL COMMENT 'Submitted service skill code array',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Application audit status',
  review_comment VARCHAR(500) DEFAULT NULL COMMENT 'Review comment',
  reviewed_by VARCHAR(32) DEFAULT NULL COMMENT 'Reviewer user ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Submitted time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (certificate_id),
  UNIQUE KEY uk_nurse_certificate_file (file_id),
  KEY idx_nurse_certificate_file_owner (file_id, nurse_id),
  KEY idx_nurse_certificate_application (application_id),
  KEY idx_nurse_certificate_current (nurse_id, audit_status, created_at),
  KEY idx_nurse_certificate_reviewer (reviewed_by),
  CONSTRAINT fk_nurse_certificate_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_certificate_file FOREIGN KEY (file_id) REFERENCES file_asset (file_id),
  CONSTRAINT fk_nurse_certificate_file_owner FOREIGN KEY (file_id, nurse_id) REFERENCES file_asset (file_id, uploaded_by),
  CONSTRAINT fk_nurse_certificate_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_certificate_audit_status CHECK (audit_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse qualification applications and certificate files';

SET @has_cert_file_owner_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'nurse_certificate'
    AND index_name = 'idx_nurse_certificate_file_owner'
);
SET @sql := IF(@has_cert_file_owner_idx = 0,
  'ALTER TABLE nurse_certificate ADD INDEX idx_nurse_certificate_file_owner (file_id, nurse_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_cert_file_owner_fk := (
  SELECT COUNT(*) FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE()
    AND constraint_name = 'fk_nurse_certificate_file_owner'
);
SET @sql := IF(@has_cert_file_owner_fk = 0,
  'ALTER TABLE nurse_certificate ADD CONSTRAINT fk_nurse_certificate_file_owner FOREIGN KEY (file_id, nurse_id) REFERENCES file_asset (file_id, uploaded_by)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS nurse_training_record (
  training_id VARCHAR(32) NOT NULL COMMENT 'Training review record ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  training_status VARCHAR(32) NOT NULL COMMENT 'Training audit status',
  training_batch VARCHAR(64) NOT NULL COMMENT 'Training batch',
  passed_at DATETIME DEFAULT NULL COMMENT 'Passed time',
  expired_at DATETIME DEFAULT NULL COMMENT 'Training qualification expiry time',
  remark VARCHAR(500) DEFAULT NULL COMMENT 'Review remark',
  reviewed_by VARCHAR(32) DEFAULT NULL COMMENT 'Reviewer user ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (training_id),
  KEY idx_nurse_training_current (nurse_id, created_at),
  KEY idx_nurse_training_status (training_status, expired_at),
  KEY idx_nurse_training_reviewer (reviewed_by),
  CONSTRAINT fk_nurse_training_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_training_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_training_status CHECK (training_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE')),
  CONSTRAINT ck_nurse_training_approved_expiry CHECK (training_status <> 'APPROVED' OR expired_at IS NOT NULL),
  CONSTRAINT ck_nurse_training_remark_required CHECK (
    training_status NOT IN ('REJECTED','NEED_MORE') OR (remark IS NOT NULL AND LENGTH(TRIM(remark)) > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse training review history';

SET @has_training_expiry_check := (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'nurse_training_record'
    AND constraint_name = 'ck_nurse_training_approved_expiry'
    AND constraint_type = 'CHECK'
);
SET @sql := IF(@has_training_expiry_check = 0,
  'ALTER TABLE nurse_training_record ADD CONSTRAINT ck_nurse_training_approved_expiry CHECK (training_status <> ''APPROVED'' OR expired_at IS NOT NULL)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_training_remark_check := (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'nurse_training_record'
    AND constraint_name = 'ck_nurse_training_remark_required'
    AND constraint_type = 'CHECK'
);
SET @sql := IF(@has_training_remark_check = 0,
  'ALTER TABLE nurse_training_record ADD CONSTRAINT ck_nurse_training_remark_required CHECK (training_status NOT IN (''REJECTED'',''NEED_MORE'') OR (remark IS NOT NULL AND LENGTH(TRIM(remark)) > 0))',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS nurse_service_skill (
  skill_id VARCHAR(32) NOT NULL COMMENT 'Nurse service skill row ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  service_id VARCHAR(32) DEFAULT NULL COMMENT 'Matched service item ID; NULL means general skill',
  skill_code VARCHAR(64) NOT NULL COMMENT 'Frozen skill code',
  skill_name VARCHAR(128) NOT NULL COMMENT 'Skill display name',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether skill is enabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (skill_id),
  UNIQUE KEY uk_nurse_service_skill (nurse_id, service_id, skill_code),
  KEY idx_nurse_service_skill_service (service_id, enabled),
  CONSTRAINT fk_nurse_service_skill_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_service_skill_service FOREIGN KEY (service_id) REFERENCES service_item (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse service skills for recommendation matching';

CREATE TABLE IF NOT EXISTS nurse_score (
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  total_score DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT 'Recommendation score source; nurses start at 100',
  service_count INT NOT NULL DEFAULT 0 COMMENT 'Completed service count',
  positive_rate DECIMAL(5,2) DEFAULT NULL COMMENT 'Positive feedback rate percentage',
  complaint_count INT NOT NULL DEFAULT 0 COMMENT 'Complaint count',
  last_service_at DATETIME DEFAULT NULL COMMENT 'Last service time',
  updated_by VARCHAR(32) DEFAULT NULL COMMENT 'Last updater user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (nurse_id),
  KEY idx_nurse_score_total (total_score),
  CONSTRAINT fk_nurse_score_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_score_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_score_total CHECK (total_score >= 0 AND total_score <= 100),
  CONSTRAINT ck_nurse_score_positive_rate CHECK (positive_rate IS NULL OR (positive_rate >= 0 AND positive_rate <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse recommendation score source';

CREATE TABLE IF NOT EXISTS nurse_recommendation_log (
  recommendation_log_id VARCHAR(32) NOT NULL COMMENT 'Recommendation candidate log ID',
  request_key VARCHAR(64) NOT NULL COMMENT 'Recommendation request key',
  request_hash CHAR(64) DEFAULT NULL COMMENT 'Stable request hash for Redis cache key',
  order_id VARCHAR(32) DEFAULT NULL COMMENT 'Order ID when recommendation is tied to an order',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  service_id VARCHAR(32) NOT NULL COMMENT 'Service item ID',
  address_id VARCHAR(32) DEFAULT NULL COMMENT 'Service address ID snapshot',
  scheduled_start_at DATETIME NOT NULL COMMENT 'Scheduled service start time',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Candidate nurse user ID',
  score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Calculated recommendation score',
  matched_skills VARCHAR(500) DEFAULT NULL COMMENT 'Matched skill code CSV snapshot',
  recommend_reason VARCHAR(500) NOT NULL COMMENT 'Human-readable recommendation reason',
  available TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether candidate can be selected now',
  unavailable_reason VARCHAR(255) DEFAULT NULL COMMENT 'Unavailable reason when available is false',
  rank_no INT DEFAULT NULL COMMENT 'Stable rank in this recommendation batch',
  candidate_snapshot JSON DEFAULT NULL COMMENT 'Candidate factor snapshot for audit',
  selected_at DATETIME DEFAULT NULL COMMENT 'When this candidate was selected as preference',
  selected_by VARCHAR(32) DEFAULT NULL COMMENT 'Selector user ID',
  created_by VARCHAR(32) NOT NULL COMMENT 'Requester user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (recommendation_log_id),
  KEY idx_nurse_recommendation_request (request_key, rank_no),
  KEY idx_nurse_recommendation_hash (request_hash),
  KEY idx_nurse_recommendation_order (order_id, score),
  KEY idx_nurse_recommendation_nurse (nurse_id, created_at),
  CONSTRAINT fk_nurse_recommendation_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_nurse_recommendation_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_nurse_recommendation_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT fk_nurse_recommendation_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_recommendation_selected_by FOREIGN KEY (selected_by) REFERENCES sys_user (user_id),
  CONSTRAINT fk_nurse_recommendation_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_recommendation_score CHECK (score >= 0 AND score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Persisted nurse recommendation candidates and reasons';

SET @has_preferred_nurse_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND column_name = 'preferred_nurse_id'
);
SET @sql := IF(@has_preferred_nurse_id = 0,
  'ALTER TABLE nursing_order ADD COLUMN preferred_nurse_id VARCHAR(32) NULL COMMENT ''Family preferred nurse ID'' AFTER remark',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preferred_nurse_reason := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND column_name = 'preferred_nurse_reason'
);
SET @sql := IF(@has_preferred_nurse_reason = 0,
  'ALTER TABLE nursing_order ADD COLUMN preferred_nurse_reason VARCHAR(500) NULL COMMENT ''Recommendation reason snapshot for preferred nurse'' AFTER preferred_nurse_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preferred_log_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND column_name = 'preferred_recommendation_log_id'
);
SET @sql := IF(@has_preferred_log_id = 0,
  'ALTER TABLE nursing_order ADD COLUMN preferred_recommendation_log_id VARCHAR(32) NULL COMMENT ''Preferred recommendation log ID'' AFTER preferred_nurse_reason',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preferred_selected_at := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND column_name = 'preferred_selected_at'
);
SET @sql := IF(@has_preferred_selected_at = 0,
  'ALTER TABLE nursing_order ADD COLUMN preferred_selected_at DATETIME NULL COMMENT ''Preferred nurse selected time'' AFTER preferred_recommendation_log_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preferred_selected_by := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND column_name = 'preferred_selected_by'
);
SET @sql := IF(@has_preferred_selected_by = 0,
  'ALTER TABLE nursing_order ADD COLUMN preferred_selected_by VARCHAR(32) NULL COMMENT ''Preferred nurse selector user ID'' AFTER preferred_selected_at',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_preferred_nurse := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'nursing_order' AND index_name = 'idx_nursing_order_preferred_nurse'
);
SET @sql := IF(@has_idx_preferred_nurse = 0,
  'ALTER TABLE nursing_order ADD INDEX idx_nursing_order_preferred_nurse (preferred_nurse_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_preferred_nurse := (
  SELECT COUNT(*) FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_nursing_order_preferred_nurse'
);
SET @sql := IF(@has_fk_preferred_nurse = 0,
  'ALTER TABLE nursing_order ADD CONSTRAINT fk_nursing_order_preferred_nurse FOREIGN KEY (preferred_nurse_id) REFERENCES sys_user (user_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_preferred_log := (
  SELECT COUNT(*) FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_nursing_order_preferred_log'
);
SET @sql := IF(@has_fk_preferred_log = 0,
  'ALTER TABLE nursing_order ADD CONSTRAINT fk_nursing_order_preferred_log FOREIGN KEY (preferred_recommendation_log_id) REFERENCES nurse_recommendation_log (recommendation_log_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS care_attention_notice (
  notice_id VARCHAR(32) NOT NULL COMMENT 'Pre-service attention notice ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  task_id VARCHAR(32) DEFAULT NULL COMMENT 'Nurse task ID',
  nurse_id VARCHAR(32) DEFAULT NULL COMMENT 'Assigned nurse user ID snapshot',
  notice_level VARCHAR(32) NOT NULL COMMENT 'Notice level',
  content VARCHAR(500) NOT NULL COMMENT 'Minimum necessary care notice content',
  source_type VARCHAR(32) NOT NULL COMMENT 'Notice source type',
  source_id VARCHAR(64) NOT NULL COMMENT 'Notice source identifier',
  source_version VARCHAR(64) DEFAULT NULL COMMENT 'Source version snapshot',
  required_ack TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether nurse acknowledgement is required',
  notice_hash CHAR(64) NOT NULL COMMENT 'Idempotency hash for normalized content and source',
  notice_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Notice status',
  generated_by VARCHAR(32) DEFAULT NULL COMMENT 'Generator user ID',
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Generated time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (notice_id),
  UNIQUE KEY uk_care_attention_notice_dedupe (order_id, source_type, source_id, notice_hash),
  KEY idx_care_attention_notice_order (order_id, notice_level),
  KEY idx_care_attention_notice_task (task_id),
  KEY idx_care_attention_notice_nurse (nurse_id, notice_status),
  CONSTRAINT fk_care_attention_notice_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_care_attention_notice_task FOREIGN KEY (task_id) REFERENCES nurse_task (task_id),
  CONSTRAINT fk_care_attention_notice_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_care_attention_notice_generated_by FOREIGN KEY (generated_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_care_attention_notice_level CHECK (notice_level IN ('INFO','WARNING','CRITICAL')),
  CONSTRAINT ck_care_attention_notice_source CHECK (source_type IN ('HEALTH_ARCHIVE','MEDICAL_FILE','SERVICE_ITEM','ORDER_CONTEXT')),
  CONSTRAINT ck_care_attention_notice_status CHECK (notice_status IN ('ACTIVE','CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Pre-service care attention notices';

CREATE TABLE IF NOT EXISTS care_attention_ack (
  ack_id VARCHAR(32) NOT NULL COMMENT 'Attention acknowledgement ID',
  notice_id VARCHAR(32) NOT NULL COMMENT 'Attention notice ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID snapshot',
  task_id VARCHAR(32) DEFAULT NULL COMMENT 'Nurse task ID snapshot',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Assigned nurse user ID',
  acked_by VARCHAR(32) NOT NULL COMMENT 'Acknowledging user ID',
  acked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Acknowledged time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (ack_id),
  UNIQUE KEY uk_care_attention_ack_notice_nurse (notice_id, nurse_id),
  KEY idx_care_attention_ack_order (order_id, nurse_id),
  CONSTRAINT fk_care_attention_ack_notice FOREIGN KEY (notice_id) REFERENCES care_attention_notice (notice_id),
  CONSTRAINT fk_care_attention_ack_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_care_attention_ack_task FOREIGN KEY (task_id) REFERENCES nurse_task (task_id),
  CONSTRAINT fk_care_attention_ack_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_care_attention_ack_acked_by FOREIGN KEY (acked_by) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Pre-service attention acknowledgement records';
