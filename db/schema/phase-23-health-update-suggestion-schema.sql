USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS health_update_suggestion (
  suggestion_id VARCHAR(32) NOT NULL COMMENT '健康档案变更建议ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  order_id VARCHAR(32) DEFAULT NULL COMMENT '订单ID',
  field_name VARCHAR(64) NOT NULL COMMENT '建议字段',
  old_value TEXT DEFAULT NULL COMMENT '原值',
  new_value TEXT NOT NULL COMMENT '建议值',
  source_type VARCHAR(64) NOT NULL COMMENT '来源类型',
  source_id VARCHAR(32) DEFAULT NULL COMMENT '来源ID',
  reason VARCHAR(255) NOT NULL COMMENT '原因',
  suggestion_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '建议状态',
  created_by VARCHAR(32) NOT NULL COMMENT '提交人',
  review_task_id VARCHAR(32) DEFAULT NULL COMMENT '审核任务ID',
  pending_dedupe_key CHAR(64) GENERATED ALWAYS AS (
    IF(suggestion_status = 'PENDING', SHA2(CONCAT_WS('|', order_id, source_type, source_id, field_name, new_value), 256), NULL)
  ) STORED COMMENT '待审核建议幂等键',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (suggestion_id),
  KEY idx_health_suggestion_elder_status (elder_id, suggestion_status),
  KEY idx_health_suggestion_order (order_id),
  UNIQUE KEY uk_health_suggestion_pending_dedupe (pending_dedupe_key),
  CONSTRAINT fk_health_suggestion_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_health_suggestion_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_health_suggestion_review_task FOREIGN KEY (review_task_id) REFERENCES health_info_review_task (review_task_id),
  CONSTRAINT ck_health_suggestion_source CHECK (source_type IN ('SERVICE_RECORD','SERVICE_REPORT')),
  CONSTRAINT ck_health_suggestion_status CHECK (suggestion_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康档案变更建议';
