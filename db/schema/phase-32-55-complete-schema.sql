USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS reminder_task (
  task_id VARCHAR(32) NOT NULL COMMENT 'Reminder task ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  reminder_type VARCHAR(32) NOT NULL COMMENT 'Reminder type',
  title VARCHAR(128) NOT NULL COMMENT 'Reminder title',
  content VARCHAR(500) DEFAULT NULL COMMENT 'Reminder content',
  scheduled_at DATETIME NOT NULL COMMENT 'Scheduled reminder time',
  reminder_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Current reminder status',
  source_type VARCHAR(64) DEFAULT NULL COMMENT 'Source business type',
  source_id VARCHAR(32) DEFAULT NULL COMMENT 'Source business ID',
  created_by VARCHAR(32) DEFAULT NULL COMMENT 'Creator user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (task_id),
  KEY idx_reminder_task_elder_time (elder_id, scheduled_at),
  KEY idx_reminder_task_status_time (reminder_status, scheduled_at),
  CONSTRAINT fk_reminder_task_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_reminder_task_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_reminder_task_type CHECK (reminder_type IN ('MEDICATION','MEASUREMENT','REHAB','REVISIT','FOLLOW_UP','CUSTOM')),
  CONSTRAINT ck_reminder_task_status CHECK (reminder_status IN ('PENDING','DONE','SNOOZED','MISSED','NEED_HELP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Elder reminder tasks';

CREATE TABLE IF NOT EXISTS reminder_record (
  record_id VARCHAR(32) NOT NULL COMMENT 'Reminder execution record ID',
  task_id VARCHAR(32) NOT NULL COMMENT 'Reminder task ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID snapshot',
  result VARCHAR(32) NOT NULL COMMENT 'Execution result',
  remark VARCHAR(500) DEFAULT NULL COMMENT 'User remark',
  snooze_minutes INT DEFAULT NULL COMMENT 'Snooze duration in minutes',
  operator_id VARCHAR(32) DEFAULT NULL COMMENT 'Operator user ID',
  operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Operated time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (record_id),
  KEY idx_reminder_record_task (task_id, operated_at),
  KEY idx_reminder_record_elder (elder_id, operated_at),
  KEY idx_reminder_record_result (result, operated_at),
  CONSTRAINT fk_reminder_record_task FOREIGN KEY (task_id) REFERENCES reminder_task (task_id),
  CONSTRAINT fk_reminder_record_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_reminder_record_operator FOREIGN KEY (operator_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_reminder_record_result CHECK (result IN ('DONE','SNOOZED','MISSED','NEED_HELP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Reminder execution records';

CREATE TABLE IF NOT EXISTS care_metric_config (
  config_id VARCHAR(32) NOT NULL COMMENT 'Care metric config ID',
  service_id VARCHAR(32) NOT NULL COMMENT 'Service item ID',
  config_version INT NOT NULL DEFAULT 1 COMMENT 'Config version',
  config_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Config status',
  created_by VARCHAR(32) DEFAULT NULL COMMENT 'Creator user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_care_metric_config_service_version (service_id, config_version),
  KEY idx_care_metric_config_service_status (service_id, config_status),
  CONSTRAINT fk_care_metric_config_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT fk_care_metric_config_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_care_metric_config_status CHECK (config_status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Service care metric config';

CREATE TABLE IF NOT EXISTS care_metric_item (
  metric_item_id VARCHAR(32) NOT NULL COMMENT 'Care metric item ID',
  config_id VARCHAR(32) NOT NULL COMMENT 'Metric config ID',
  service_id VARCHAR(32) NOT NULL COMMENT 'Service item ID snapshot',
  metric_code VARCHAR(64) NOT NULL COMMENT 'Metric code',
  metric_name VARCHAR(128) NOT NULL COMMENT 'Metric name',
  metric_type VARCHAR(32) NOT NULL COMMENT 'Metric type',
  required TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether required',
  evidence_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'Required evidence type',
  expected_action VARCHAR(500) DEFAULT NULL COMMENT 'Expected action',
  score_weight DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Score weight',
  description VARCHAR(500) DEFAULT NULL COMMENT 'Description',
  sort INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether enabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (metric_item_id),
  UNIQUE KEY uk_care_metric_item_code (config_id, metric_code),
  KEY idx_care_metric_item_service (service_id, enabled, sort),
  CONSTRAINT fk_care_metric_item_config FOREIGN KEY (config_id) REFERENCES care_metric_config (config_id),
  CONSTRAINT fk_care_metric_item_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT ck_care_metric_item_type CHECK (metric_type IN ('PRE_SERVICE','SERVICE_PROCESS','POST_SERVICE')),
  CONSTRAINT ck_care_metric_item_evidence CHECK (evidence_type IN ('NONE','PHOTO','FILE','TEXT','VITAL_SIGN')),
  CONSTRAINT ck_care_metric_item_weight CHECK (score_weight >= 0 AND score_weight <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Service care metric items';

CREATE TABLE IF NOT EXISTS metric_score_rule (
  score_rule_id VARCHAR(32) NOT NULL COMMENT 'Metric score rule ID',
  metric_item_id VARCHAR(32) DEFAULT NULL COMMENT 'Metric item ID',
  rule_type VARCHAR(32) NOT NULL COMMENT 'Rule type',
  score_delta DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT 'Score delta',
  description VARCHAR(500) DEFAULT NULL COMMENT 'Rule description',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether enabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (score_rule_id),
  KEY idx_metric_score_rule_item (metric_item_id, enabled),
  CONSTRAINT fk_metric_score_rule_item FOREIGN KEY (metric_item_id) REFERENCES care_metric_item (metric_item_id),
  CONSTRAINT ck_metric_score_rule_type CHECK (rule_type IN ('PASS','MISSING','PENDING_PROOF','EXEMPT_APPROVED','EXEMPT_REJECTED','COMPLAINT','APPEAL','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metric score rules';

CREATE TABLE IF NOT EXISTS order_metric_checklist (
  checklist_id VARCHAR(32) NOT NULL COMMENT 'Order metric checklist ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  service_id VARCHAR(32) NOT NULL COMMENT 'Service item ID',
  config_id VARCHAR(32) NOT NULL COMMENT 'Metric config ID',
  config_version INT NOT NULL COMMENT 'Metric config version snapshot',
  generated_by VARCHAR(32) DEFAULT NULL COMMENT 'Generator user ID',
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Generated time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (checklist_id),
  UNIQUE KEY uk_order_metric_checklist_order (order_id),
  KEY idx_order_metric_checklist_service (service_id),
  CONSTRAINT fk_order_metric_checklist_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_order_metric_checklist_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT fk_order_metric_checklist_config FOREIGN KEY (config_id) REFERENCES care_metric_config (config_id),
  CONSTRAINT fk_order_metric_checklist_generated_by FOREIGN KEY (generated_by) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Order metric checklist generated from service config';

CREATE TABLE IF NOT EXISTS order_metric_item (
  order_metric_item_id VARCHAR(32) NOT NULL COMMENT 'Order metric item ID',
  checklist_id VARCHAR(32) NOT NULL COMMENT 'Order metric checklist ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  metric_item_id VARCHAR(32) NOT NULL COMMENT 'Metric item ID',
  metric_code VARCHAR(64) NOT NULL COMMENT 'Metric code snapshot',
  metric_name VARCHAR(128) NOT NULL COMMENT 'Metric name snapshot',
  required TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether required',
  evidence_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'Evidence type snapshot',
  score_weight DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Score weight snapshot',
  metric_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Metric status',
  submitted_at DATETIME DEFAULT NULL COMMENT 'Submitted time',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (order_metric_item_id),
  UNIQUE KEY uk_order_metric_item_code (order_id, metric_code),
  KEY idx_order_metric_item_checklist (checklist_id),
  KEY idx_order_metric_item_status (metric_status, order_id),
  CONSTRAINT fk_order_metric_item_checklist FOREIGN KEY (checklist_id) REFERENCES order_metric_checklist (checklist_id),
  CONSTRAINT fk_order_metric_item_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_order_metric_item_metric FOREIGN KEY (metric_item_id) REFERENCES care_metric_item (metric_item_id),
  CONSTRAINT ck_order_metric_item_evidence CHECK (evidence_type IN ('NONE','PHOTO','FILE','TEXT','VITAL_SIGN')),
  CONSTRAINT ck_order_metric_item_status CHECK (metric_status IN ('PENDING','SUBMITTED','PASS','MISSING','PENDING_PROOF','EXEMPT_APPROVED','EXEMPT_REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Order metric execution items';

CREATE TABLE IF NOT EXISTS care_service_evidence (
  evidence_id VARCHAR(32) NOT NULL COMMENT 'Care service evidence ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  task_id VARCHAR(32) DEFAULT NULL COMMENT 'Nurse task ID',
  order_metric_item_id VARCHAR(32) DEFAULT NULL COMMENT 'Order metric item ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  file_id VARCHAR(32) DEFAULT NULL COMMENT 'Evidence file ID',
  evidence_type VARCHAR(32) NOT NULL COMMENT 'Evidence type',
  description VARCHAR(500) DEFAULT NULL COMMENT 'Evidence description',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Evidence audit status',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Submitted time',
  reviewed_by VARCHAR(32) DEFAULT NULL COMMENT 'Reviewer user ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  review_comment VARCHAR(500) DEFAULT NULL COMMENT 'Review comment',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (evidence_id),
  KEY idx_care_evidence_order (order_id, audit_status),
  KEY idx_care_evidence_metric (order_metric_item_id, audit_status),
  KEY idx_care_evidence_nurse (nurse_id, submitted_at),
  CONSTRAINT fk_care_evidence_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_care_evidence_task FOREIGN KEY (task_id) REFERENCES nurse_task (task_id),
  CONSTRAINT fk_care_evidence_metric FOREIGN KEY (order_metric_item_id) REFERENCES order_metric_item (order_metric_item_id),
  CONSTRAINT fk_care_evidence_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_care_evidence_file FOREIGN KEY (file_id) REFERENCES file_asset (file_id),
  CONSTRAINT fk_care_evidence_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_care_evidence_type CHECK (evidence_type IN ('PHOTO','FILE','TEXT','VITAL_SIGN')),
  CONSTRAINT ck_care_evidence_audit CHECK (audit_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Care service evidence files and text';

CREATE TABLE IF NOT EXISTS evidence_review_record (
  review_record_id VARCHAR(32) NOT NULL COMMENT 'Evidence review record ID',
  evidence_id VARCHAR(32) NOT NULL COMMENT 'Evidence ID',
  from_status VARCHAR(32) DEFAULT NULL COMMENT 'Previous audit status',
  to_status VARCHAR(32) NOT NULL COMMENT 'Target audit status',
  review_comment VARCHAR(500) DEFAULT NULL COMMENT 'Review comment',
  reviewer_id VARCHAR(32) NOT NULL COMMENT 'Reviewer user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (review_record_id),
  KEY idx_evidence_review_evidence (evidence_id, created_at),
  CONSTRAINT fk_evidence_review_evidence FOREIGN KEY (evidence_id) REFERENCES care_service_evidence (evidence_id),
  CONSTRAINT fk_evidence_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_evidence_review_to_status CHECK (to_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Evidence audit history';

CREATE TABLE IF NOT EXISTS nurse_metric_record (
  metric_record_id VARCHAR(32) NOT NULL COMMENT 'Nurse metric record ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  order_id VARCHAR(32) DEFAULT NULL COMMENT 'Order ID',
  order_metric_item_id VARCHAR(32) DEFAULT NULL COMMENT 'Order metric item ID',
  metric_status VARCHAR(32) NOT NULL COMMENT 'Metric status',
  score_delta DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT 'Score impact',
  source_type VARCHAR(64) NOT NULL COMMENT 'Source business type',
  source_id VARCHAR(32) DEFAULT NULL COMMENT 'Source business ID',
  recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Recorded time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (metric_record_id),
  KEY idx_nurse_metric_record_nurse (nurse_id, recorded_at),
  KEY idx_nurse_metric_record_order (order_id),
  CONSTRAINT fk_nurse_metric_record_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_metric_record_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_nurse_metric_record_item FOREIGN KEY (order_metric_item_id) REFERENCES order_metric_item (order_metric_item_id),
  CONSTRAINT ck_nurse_metric_record_status CHECK (metric_status IN ('PENDING','SUBMITTED','PASS','MISSING','PENDING_PROOF','EXEMPT_APPROVED','EXEMPT_REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse metric score source records';

CREATE TABLE IF NOT EXISTS metric_exception_proof (
  proof_id VARCHAR(32) NOT NULL COMMENT 'Metric exception proof ID',
  order_metric_item_id VARCHAR(32) NOT NULL COMMENT 'Order metric item ID',
  evidence_id VARCHAR(32) DEFAULT NULL COMMENT 'Related evidence ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  reason_type VARCHAR(32) NOT NULL COMMENT 'Exception reason type',
  reason VARCHAR(500) NOT NULL COMMENT 'Exception reason',
  proof_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Proof review status',
  review_comment VARCHAR(500) DEFAULT NULL COMMENT 'Review comment',
  reviewed_by VARCHAR(32) DEFAULT NULL COMMENT 'Reviewer user ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (proof_id),
  KEY idx_metric_exception_item (order_metric_item_id, proof_status),
  KEY idx_metric_exception_nurse (nurse_id, created_at),
  CONSTRAINT fk_metric_exception_item FOREIGN KEY (order_metric_item_id) REFERENCES order_metric_item (order_metric_item_id),
  CONSTRAINT fk_metric_exception_evidence FOREIGN KEY (evidence_id) REFERENCES care_service_evidence (evidence_id),
  CONSTRAINT fk_metric_exception_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_metric_exception_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_metric_exception_reason CHECK (reason_type IN ('FORGOT','NOT_REQUIRED','ELDER_REFUSED','OBJECTIVE_IMPOSSIBLE','OTHER')),
  CONSTRAINT ck_metric_exception_status CHECK (proof_status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metric exception proof';

CREATE TABLE IF NOT EXISTS ai_assistant_session (
  session_id VARCHAR(32) NOT NULL COMMENT 'AI assistant session ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  user_id VARCHAR(32) NOT NULL COMMENT 'Requester user ID',
  session_title VARCHAR(128) DEFAULT NULL COMMENT 'Session title',
  session_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Session status',
  safety_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'Safety level',
  risk_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether risk flag exists',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT 'Trace ID',
  source_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'Session source type',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (session_id),
  KEY idx_ai_session_elder (elder_id, created_at),
  KEY idx_ai_session_user (user_id, created_at),
  KEY idx_ai_session_risk (risk_flag, safety_level),
  CONSTRAINT fk_ai_session_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_ai_session_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_ai_session_status CHECK (session_status IN ('ACTIVE','CLOSED')),
  CONSTRAINT ck_ai_session_safety CHECK (safety_level IN ('NORMAL','WARNING','CRITICAL')),
  CONSTRAINT ck_ai_session_source CHECK (source_type IN ('TEXT','VOICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI assistant session log';

CREATE TABLE IF NOT EXISTS ai_assistant_message (
  message_id VARCHAR(32) NOT NULL COMMENT 'AI assistant message ID',
  session_id VARCHAR(32) NOT NULL COMMENT 'AI session ID',
  sender_role VARCHAR(32) NOT NULL COMMENT 'Sender role',
  message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'Message type',
  content_summary VARCHAR(500) DEFAULT NULL COMMENT 'Message summary',
  content_text TEXT DEFAULT NULL COMMENT 'Message text for audit summary',
  voice_log_id VARCHAR(32) DEFAULT NULL COMMENT 'Voice command log ID',
  safety_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether safety flag exists',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT 'Trace ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (message_id),
  KEY idx_ai_message_session (session_id, created_at),
  CONSTRAINT fk_ai_message_session FOREIGN KEY (session_id) REFERENCES ai_assistant_session (session_id),
  CONSTRAINT fk_ai_message_voice FOREIGN KEY (voice_log_id) REFERENCES voice_command_log (voice_log_id),
  CONSTRAINT ck_ai_message_sender CHECK (sender_role IN ('USER','ASSISTANT','SYSTEM')),
  CONSTRAINT ck_ai_message_type CHECK (message_type IN ('TEXT','VOICE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI assistant message log';

SET @has_voice_session_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_command_log' AND column_name = 'session_id'
);
SET @sql := IF(@has_voice_session_id = 0,
  'ALTER TABLE voice_command_log ADD COLUMN session_id VARCHAR(32) NULL COMMENT ''AI session ID'' AFTER source_biz_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_voice_trace_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_command_log' AND column_name = 'trace_id'
);
SET @sql := IF(@has_voice_trace_id = 0,
  'ALTER TABLE voice_command_log ADD COLUMN trace_id VARCHAR(64) NULL COMMENT ''Trace ID'' AFTER session_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_voice_safety_flag := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_command_log' AND column_name = 'safety_flag'
);
SET @sql := IF(@has_voice_safety_flag = 0,
  'ALTER TABLE voice_command_log ADD COLUMN safety_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Whether safety flag exists'' AFTER trace_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_voice_risk_level := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_command_log' AND column_name = 'risk_level'
);
SET @sql := IF(@has_voice_risk_level = 0,
  'ALTER TABLE voice_command_log ADD COLUMN risk_level VARCHAR(32) NULL COMMENT ''Risk level'' AFTER safety_flag',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS assistance_ticket (
  assistance_ticket_id VARCHAR(32) NOT NULL COMMENT 'Assistance source ticket ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  requester_id VARCHAR(32) NOT NULL COMMENT 'Requester user ID',
  session_id VARCHAR(32) DEFAULT NULL COMMENT 'AI session source ID',
  category VARCHAR(64) NOT NULL COMMENT 'Assistance category',
  priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'Priority',
  ticket_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Ticket status',
  description VARCHAR(500) NOT NULL COMMENT 'Assistance description',
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'Source type',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (assistance_ticket_id),
  KEY idx_assistance_ticket_elder (elder_id, created_at),
  KEY idx_assistance_ticket_status (ticket_status, priority),
  CONSTRAINT fk_assistance_ticket_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_assistance_ticket_requester FOREIGN KEY (requester_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_assistance_ticket_session FOREIGN KEY (session_id) REFERENCES ai_assistant_session (session_id),
  CONSTRAINT ck_assistance_ticket_priority CHECK (priority IN ('NORMAL','URGENT')),
  CONSTRAINT ck_assistance_ticket_status CHECK (ticket_status IN ('PENDING','PROCESSING','RESOLVED','CLOSED')),
  CONSTRAINT ck_assistance_ticket_source CHECK (source_type IN ('AI','REMINDER','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User assistance source ticket';

CREATE TABLE IF NOT EXISTS customer_service_ticket (
  ticket_id VARCHAR(32) NOT NULL COMMENT 'Customer service ticket ID',
  assistance_ticket_id VARCHAR(32) DEFAULT NULL COMMENT 'Assistance source ticket ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  requester_id VARCHAR(32) DEFAULT NULL COMMENT 'Requester user ID',
  assigned_to VARCHAR(32) DEFAULT NULL COMMENT 'Customer service user ID',
  category VARCHAR(64) NOT NULL COMMENT 'Ticket category',
  priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'Priority',
  ticket_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Ticket status',
  description VARCHAR(500) NOT NULL COMMENT 'Ticket description',
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'Source type',
  source_id VARCHAR(32) DEFAULT NULL COMMENT 'Source business ID',
  resolved_at DATETIME DEFAULT NULL COMMENT 'Resolved time',
  closed_at DATETIME DEFAULT NULL COMMENT 'Closed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (ticket_id),
  KEY idx_cs_ticket_elder (elder_id, created_at),
  KEY idx_cs_ticket_status (ticket_status, priority),
  KEY idx_cs_ticket_assignee (assigned_to, ticket_status),
  CONSTRAINT fk_cs_ticket_assistance FOREIGN KEY (assistance_ticket_id) REFERENCES assistance_ticket (assistance_ticket_id),
  CONSTRAINT fk_cs_ticket_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_cs_ticket_requester FOREIGN KEY (requester_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_cs_ticket_assignee FOREIGN KEY (assigned_to) REFERENCES sys_user (user_id),
  CONSTRAINT ck_cs_ticket_priority CHECK (priority IN ('NORMAL','URGENT')),
  CONSTRAINT ck_cs_ticket_status CHECK (ticket_status IN ('PENDING','PROCESSING','RESOLVED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Customer service ticket';

CREATE TABLE IF NOT EXISTS ticket_message (
  message_id VARCHAR(32) NOT NULL COMMENT 'Ticket message ID',
  ticket_id VARCHAR(32) NOT NULL COMMENT 'Customer service ticket ID',
  sender_id VARCHAR(32) DEFAULT NULL COMMENT 'Sender user ID',
  sender_role VARCHAR(64) NOT NULL COMMENT 'Sender role',
  message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'Message type',
  content VARCHAR(1000) NOT NULL COMMENT 'Message content',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (message_id),
  KEY idx_ticket_message_ticket (ticket_id, created_at),
  CONSTRAINT fk_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES customer_service_ticket (ticket_id),
  CONSTRAINT fk_ticket_message_sender FOREIGN KEY (sender_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_ticket_message_type CHECK (message_type IN ('TEXT','PHONE_NOTE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Customer service ticket messages';

CREATE TABLE IF NOT EXISTS review (
  review_id VARCHAR(32) NOT NULL COMMENT 'Service review ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  report_id VARCHAR(32) DEFAULT NULL COMMENT 'Service report ID',
  reviewer_id VARCHAR(32) NOT NULL COMMENT 'Reviewer user ID',
  reviewer_role VARCHAR(32) NOT NULL COMMENT 'Reviewer role',
  rating INT NOT NULL COMMENT 'Rating 1-5',
  satisfaction INT DEFAULT NULL COMMENT 'Satisfaction 1-5',
  content VARCHAR(500) DEFAULT NULL COMMENT 'Review content',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (review_id),
  KEY idx_review_order (order_id),
  KEY idx_review_reviewer (reviewer_id, created_at),
  CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_review_report FOREIGN KEY (report_id) REFERENCES service_report (report_id),
  CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_review_role CHECK (reviewer_role IN ('ELDER','FAMILY')),
  CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT ck_review_satisfaction CHECK (satisfaction IS NULL OR satisfaction BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Service review';

CREATE TABLE IF NOT EXISTS complaint (
  complaint_id VARCHAR(32) NOT NULL COMMENT 'Complaint ID',
  review_id VARCHAR(32) DEFAULT NULL COMMENT 'Review ID',
  order_id VARCHAR(32) NOT NULL COMMENT 'Order ID',
  complainant_id VARCHAR(32) NOT NULL COMMENT 'Complainant user ID',
  complaint_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Complaint status',
  content VARCHAR(1000) NOT NULL COMMENT 'Complaint content',
  handled_by VARCHAR(32) DEFAULT NULL COMMENT 'Handler user ID',
  handle_result VARCHAR(1000) DEFAULT NULL COMMENT 'Handle result',
  handled_at DATETIME DEFAULT NULL COMMENT 'Handled time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (complaint_id),
  KEY idx_complaint_order (order_id),
  KEY idx_complaint_status (complaint_status, created_at),
  CONSTRAINT fk_complaint_review FOREIGN KEY (review_id) REFERENCES review (review_id),
  CONSTRAINT fk_complaint_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_complaint_user FOREIGN KEY (complainant_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_complaint_handler FOREIGN KEY (handled_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_complaint_status CHECK (complaint_status IN ('PENDING','PROCESSING','RESOLVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Service complaint';

CREATE TABLE IF NOT EXISTS nurse_appeal (
  appeal_id VARCHAR(32) NOT NULL COMMENT 'Nurse appeal ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  target_type VARCHAR(64) NOT NULL COMMENT 'Appeal target type',
  target_id VARCHAR(32) NOT NULL COMMENT 'Appeal target ID',
  reason VARCHAR(1000) NOT NULL COMMENT 'Appeal reason',
  file_ids JSON DEFAULT NULL COMMENT 'Appeal file IDs',
  appeal_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Appeal status',
  score_adjustment DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT 'Score adjustment',
  review_comment VARCHAR(500) DEFAULT NULL COMMENT 'Review comment',
  reviewed_by VARCHAR(32) DEFAULT NULL COMMENT 'Reviewer user ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT 'Reviewed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (appeal_id),
  KEY idx_nurse_appeal_nurse (nurse_id, created_at),
  KEY idx_nurse_appeal_status (appeal_status, created_at),
  CONSTRAINT fk_nurse_appeal_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT fk_nurse_appeal_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_appeal_status CHECK (appeal_status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse appeal records';

CREATE TABLE IF NOT EXISTS nurse_score_change_log (
  change_log_id VARCHAR(32) NOT NULL COMMENT 'Nurse score change log ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  source_event_type VARCHAR(64) NOT NULL COMMENT 'Source event type',
  source_event_id VARCHAR(32) DEFAULT NULL COMMENT 'Source event ID',
  before_score DECIMAL(5,2) NOT NULL COMMENT 'Score before change',
  after_score DECIMAL(5,2) NOT NULL COMMENT 'Score after change',
  score_delta DECIMAL(6,2) NOT NULL COMMENT 'Score delta',
  reason VARCHAR(500) NOT NULL COMMENT 'Readable change reason',
  changed_by VARCHAR(32) DEFAULT NULL COMMENT 'Operator user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (change_log_id),
  KEY idx_nurse_score_log_nurse (nurse_id, created_at),
  KEY idx_nurse_score_log_source (source_event_type, source_event_id),
  CONSTRAINT fk_nurse_score_log_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_score (nurse_id),
  CONSTRAINT fk_nurse_score_log_changed_by FOREIGN KEY (changed_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_score_log_before CHECK (before_score >= 0 AND before_score <= 100),
  CONSTRAINT ck_nurse_score_log_after CHECK (after_score >= 0 AND after_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse score change history';

CREATE TABLE IF NOT EXISTS training_article (
  article_id VARCHAR(32) NOT NULL COMMENT 'Training article ID',
  title VARCHAR(128) NOT NULL COMMENT 'Article title',
  content_summary VARCHAR(500) DEFAULT NULL COMMENT 'Article summary',
  content_url VARCHAR(255) DEFAULT NULL COMMENT 'Article content URL',
  file_id VARCHAR(32) DEFAULT NULL COMMENT 'Article file ID',
  service_id VARCHAR(32) DEFAULT NULL COMMENT 'Related service item ID',
  required_reading TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether required reading',
  article_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Article status',
  created_by VARCHAR(32) DEFAULT NULL COMMENT 'Creator user ID',
  published_at DATETIME DEFAULT NULL COMMENT 'Published time',
  offline_at DATETIME DEFAULT NULL COMMENT 'Offline time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (article_id),
  KEY idx_training_article_status (article_status, published_at),
  KEY idx_training_article_service (service_id, article_status),
  CONSTRAINT fk_training_article_file FOREIGN KEY (file_id) REFERENCES file_asset (file_id),
  CONSTRAINT fk_training_article_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT fk_training_article_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_training_article_status CHECK (article_status IN ('DRAFT','PUBLISHED','OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse training articles';

CREATE TABLE IF NOT EXISTS article_tag (
  tag_id VARCHAR(32) NOT NULL COMMENT 'Article tag row ID',
  article_id VARCHAR(32) NOT NULL COMMENT 'Training article ID',
  tag_code VARCHAR(64) NOT NULL COMMENT 'Tag code',
  tag_name VARCHAR(128) NOT NULL COMMENT 'Tag name',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (tag_id),
  UNIQUE KEY uk_article_tag (article_id, tag_code),
  CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES training_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Training article tags';

CREATE TABLE IF NOT EXISTS article_recommend_rule (
  rule_id VARCHAR(32) NOT NULL COMMENT 'Article recommend rule ID',
  article_id VARCHAR(32) NOT NULL COMMENT 'Training article ID',
  service_id VARCHAR(32) DEFAULT NULL COMMENT 'Related service item ID',
  care_level VARCHAR(32) DEFAULT NULL COMMENT 'Elder care level condition',
  skill_code VARCHAR(64) DEFAULT NULL COMMENT 'Nurse skill condition',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether enabled',
  sort INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (rule_id),
  KEY idx_article_rule_article (article_id, enabled),
  KEY idx_article_rule_service (service_id, enabled),
  CONSTRAINT fk_article_rule_article FOREIGN KEY (article_id) REFERENCES training_article (article_id),
  CONSTRAINT fk_article_rule_service FOREIGN KEY (service_id) REFERENCES service_item (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Training article recommendation rules';

CREATE TABLE IF NOT EXISTS nurse_article_reading (
  reading_id VARCHAR(32) NOT NULL COMMENT 'Nurse article reading ID',
  article_id VARCHAR(32) NOT NULL COMMENT 'Training article ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT 'Nurse user ID',
  reading_status VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT 'Reading status',
  read_at DATETIME DEFAULT NULL COMMENT 'Read time',
  confirmed_at DATETIME DEFAULT NULL COMMENT 'Confirmed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (reading_id),
  UNIQUE KEY uk_nurse_article_reading (article_id, nurse_id),
  KEY idx_nurse_article_reading_nurse (nurse_id, reading_status),
  CONSTRAINT fk_nurse_article_reading_article FOREIGN KEY (article_id) REFERENCES training_article (article_id),
  CONSTRAINT fk_nurse_article_reading_nurse FOREIGN KEY (nurse_id) REFERENCES nurse_profile (nurse_id),
  CONSTRAINT ck_nurse_article_reading_status CHECK (reading_status IN ('UNREAD','READ','CONFIRMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nurse training article reading records';

CREATE TABLE IF NOT EXISTS follow_up_record (
  follow_up_id VARCHAR(32) NOT NULL COMMENT 'Follow-up record ID',
  elder_id VARCHAR(32) NOT NULL COMMENT 'Elder ID',
  order_id VARCHAR(32) DEFAULT NULL COMMENT 'Order ID',
  ticket_id VARCHAR(32) DEFAULT NULL COMMENT 'Customer service ticket ID',
  follow_up_type VARCHAR(32) NOT NULL COMMENT 'Follow-up type',
  content VARCHAR(1000) NOT NULL COMMENT 'Follow-up content',
  next_follow_up_at DATETIME DEFAULT NULL COMMENT 'Next follow-up time',
  need_reminder TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether reminder is needed',
  created_reminder_task_id VARCHAR(32) DEFAULT NULL COMMENT 'Generated reminder task ID',
  created_by VARCHAR(32) NOT NULL COMMENT 'Creator user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (follow_up_id),
  KEY idx_follow_up_elder (elder_id, created_at),
  KEY idx_follow_up_next (next_follow_up_at),
  CONSTRAINT fk_follow_up_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_follow_up_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_follow_up_ticket FOREIGN KEY (ticket_id) REFERENCES customer_service_ticket (ticket_id),
  CONSTRAINT fk_follow_up_reminder FOREIGN KEY (created_reminder_task_id) REFERENCES reminder_task (task_id),
  CONSTRAINT fk_follow_up_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_follow_up_type CHECK (follow_up_type IN ('PHONE','ONLINE','HOME','AI','CUSTOMER_SERVICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Elder follow-up records';

CREATE TABLE IF NOT EXISTS bug_list (
  bug_id VARCHAR(32) NOT NULL COMMENT 'Delivery bug ID',
  related_stage VARCHAR(32) DEFAULT NULL COMMENT 'Related stage',
  bug_title VARCHAR(255) NOT NULL COMMENT 'Bug title',
  severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'Bug severity',
  bug_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'Bug status',
  owner VARCHAR(64) DEFAULT NULL COMMENT 'Owner',
  workaround VARCHAR(1000) DEFAULT NULL COMMENT 'Workaround or closing note',
  closed_at DATETIME DEFAULT NULL COMMENT 'Closed time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (bug_id),
  KEY idx_bug_list_status (bug_status, severity),
  CONSTRAINT ck_bug_list_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_bug_list_status CHECK (bug_status IN ('OPEN','PROCESSING','CLOSED','DEFERRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Delivery bug list for final acceptance';
