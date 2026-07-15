USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS reminder_task (
  reminder_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  content VARCHAR(512) NOT NULL,
  reminder_at DATETIME NOT NULL,
  reminder_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  snoozed_until DATETIME DEFAULT NULL,
  completed_at DATETIME DEFAULT NULL,
  needs_help_at DATETIME DEFAULT NULL,
  created_by VARCHAR(32) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (reminder_id),
  UNIQUE KEY uk_reminder_task_idempotency (elder_id, source_type, source_id, content),
  KEY idx_reminder_task_elder_status_time (elder_id, reminder_status, reminder_at),
  KEY idx_reminder_task_elder_time (elder_id, reminder_at),
  CONSTRAINT fk_reminder_task_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT ck_reminder_task_status CHECK (reminder_status IN ('PENDING','DONE','SNOOZED','MISSED','NEED_HELP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长辈提醒任务';

CREATE TABLE IF NOT EXISTS reminder_record (
  record_id VARCHAR(32) NOT NULL,
  reminder_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  from_status VARCHAR(32) DEFAULT NULL,
  to_status VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  acted_by VARCHAR(32) NOT NULL,
  note VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (record_id),
  KEY idx_reminder_record_elder_time (elder_id, created_at),
  KEY idx_reminder_record_reminder_time (reminder_id, created_at),
  CONSTRAINT fk_reminder_record_task FOREIGN KEY (reminder_id) REFERENCES reminder_task (reminder_id),
  CONSTRAINT fk_reminder_record_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT ck_reminder_record_status CHECK (to_status IN ('PENDING','DONE','SNOOZED','MISSED','NEED_HELP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒执行记录';
