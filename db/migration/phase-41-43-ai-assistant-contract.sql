USE smart_nursing;
SET NAMES utf8mb4;

-- Explicit migration for existing Docker volumes. The consolidated phase
-- 32-55 schema may be blocked by unrelated historical tables on an old volume.
CREATE TABLE IF NOT EXISTS ai_assistant_session (
  session_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  session_title VARCHAR(128) DEFAULT NULL,
  session_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  safety_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  risk_flag TINYINT(1) NOT NULL DEFAULT 0,
  trace_id VARCHAR(64) DEFAULT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (session_id),
  KEY idx_ai_session_elder (elder_id, created_at),
  KEY idx_ai_session_user (user_id, created_at),
  KEY idx_ai_session_risk (risk_flag, safety_level),
  CONSTRAINT fk_ai_session_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_ai_session_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_ai_session_status CHECK (session_status IN ('ACTIVE','CLOSED')),
  CONSTRAINT ck_ai_session_safety CHECK (safety_level IN ('NORMAL','WARNING','CRITICAL')),
  CONSTRAINT ck_ai_session_source CHECK (source_type IN ('TEXT','VOICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_assistant_message (
  message_id VARCHAR(32) NOT NULL,
  session_id VARCHAR(32) NOT NULL,
  sender_role VARCHAR(32) NOT NULL,
  message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
  content_summary VARCHAR(500) DEFAULT NULL,
  content_text TEXT DEFAULT NULL,
  voice_log_id VARCHAR(32) DEFAULT NULL,
  safety_flag TINYINT(1) NOT NULL DEFAULT 0,
  trace_id VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (message_id),
  KEY idx_ai_message_session (session_id, created_at),
  CONSTRAINT fk_ai_message_session FOREIGN KEY (session_id) REFERENCES ai_assistant_session (session_id),
  CONSTRAINT fk_ai_message_voice FOREIGN KEY (voice_log_id) REFERENCES voice_command_log (voice_log_id),
  CONSTRAINT ck_ai_message_sender CHECK (sender_role IN ('USER','ASSISTANT','SYSTEM')),
  CONSTRAINT ck_ai_message_type CHECK (message_type IN ('TEXT','VOICE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS assistance_ticket (
  assistance_ticket_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  requester_id VARCHAR(32) NOT NULL,
  session_id VARCHAR(32) DEFAULT NULL,
  category VARCHAR(64) NOT NULL,
  priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  ticket_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  description VARCHAR(500) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (assistance_ticket_id),
  KEY idx_assistance_ticket_elder (elder_id, created_at),
  KEY idx_assistance_ticket_status (ticket_status, priority),
  CONSTRAINT fk_assistance_ticket_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_assistance_ticket_requester FOREIGN KEY (requester_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_assistance_ticket_session FOREIGN KEY (session_id) REFERENCES ai_assistant_session (session_id),
  CONSTRAINT ck_assistance_ticket_priority CHECK (priority IN ('NORMAL','URGENT')),
  CONSTRAINT ck_assistance_ticket_status CHECK (ticket_status IN ('PENDING','PROCESSING','RESOLVED','CLOSED')),
  CONSTRAINT ck_assistance_ticket_source CHECK (source_type IN ('AI','REMINDER','MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS customer_service_ticket (
  ticket_id VARCHAR(32) NOT NULL,
  assistance_ticket_id VARCHAR(32) DEFAULT NULL,
  elder_id VARCHAR(32) NOT NULL,
  requester_id VARCHAR(32) DEFAULT NULL,
  assigned_to VARCHAR(32) DEFAULT NULL,
  category VARCHAR(64) NOT NULL,
  priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  ticket_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  description VARCHAR(500) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  source_id VARCHAR(32) DEFAULT NULL,
  resolved_at DATETIME DEFAULT NULL,
  closed_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ticket_message (
  message_id VARCHAR(32) NOT NULL,
  ticket_id VARCHAR(32) NOT NULL,
  sender_id VARCHAR(32) DEFAULT NULL,
  sender_role VARCHAR(64) NOT NULL,
  message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (message_id),
  KEY idx_ticket_message_ticket (ticket_id, created_at),
  CONSTRAINT fk_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES customer_service_ticket (ticket_id),
  CONSTRAINT fk_ticket_message_sender FOREIGN KEY (sender_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_ticket_message_type CHECK (message_type IN ('TEXT','PHONE_NOTE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
