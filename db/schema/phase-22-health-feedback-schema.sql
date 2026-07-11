USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS elder_health_feedback (
  feedback_id VARCHAR(32) NOT NULL COMMENT '健康反馈ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  feedback_type VARCHAR(32) NOT NULL COMMENT '反馈类型',
  severity VARCHAR(32) NOT NULL COMMENT '严重程度',
  content VARCHAR(512) DEFAULT NULL COMMENT '反馈内容',
  input_type VARCHAR(32) NOT NULL COMMENT '输入类型',
  file_id VARCHAR(32) DEFAULT NULL COMMENT '语音文件ID',
  created_by VARCHAR(32) NOT NULL COMMENT '创建人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (feedback_id),
  KEY idx_health_feedback_elder_created (elder_id, created_at),
  CONSTRAINT fk_health_feedback_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT ck_health_feedback_input CHECK (input_type IN ('BUTTON','TEXT','VOICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长辈健康反馈';

CREATE TABLE IF NOT EXISTS voice_command_log (
  voice_log_id VARCHAR(32) NOT NULL COMMENT '语音日志ID',
  user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
  file_id VARCHAR(32) DEFAULT NULL COMMENT '文件ID',
  intent_type VARCHAR(64) DEFAULT NULL COMMENT '意图类型',
  source_biz_type VARCHAR(64) DEFAULT NULL COMMENT '来源业务类型',
  source_biz_id VARCHAR(32) DEFAULT NULL COMMENT '来源业务ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (voice_log_id),
  KEY idx_voice_log_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='语音指令日志';
