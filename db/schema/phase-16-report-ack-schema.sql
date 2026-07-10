USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS care_report_ack (
  ack_id VARCHAR(32) NOT NULL COMMENT '报告确认ID',
  report_id VARCHAR(32) NOT NULL COMMENT '服务报告ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  ack_user_id VARCHAR(32) NOT NULL COMMENT '确认人用户ID',
  ack_role VARCHAR(32) NOT NULL COMMENT '确认人角色',
  ack_result VARCHAR(32) NOT NULL COMMENT '确认结果',
  satisfaction INT DEFAULT NULL COMMENT '满意度评分',
  remark VARCHAR(255) DEFAULT NULL COMMENT '确认备注',
  accepted_suggestion_ids JSON DEFAULT NULL COMMENT '采纳的归档建议ID列表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (ack_id),
  UNIQUE KEY uk_care_report_ack_report (report_id),
  KEY idx_care_report_ack_order (order_id),
  KEY idx_care_report_ack_user (ack_user_id),
  CONSTRAINT fk_care_report_ack_report FOREIGN KEY (report_id) REFERENCES service_report (report_id),
  CONSTRAINT fk_care_report_ack_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_care_report_ack_user FOREIGN KEY (ack_user_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_care_report_ack_role CHECK (ack_role IN ('ELDER','FAMILY')),
  CONSTRAINT ck_care_report_ack_result CHECK (ack_result IN ('ACCEPTED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务报告确认';

CREATE TABLE IF NOT EXISTS health_info_review_task (
  review_task_id VARCHAR(32) NOT NULL COMMENT '健康信息审核任务ID',
  report_id VARCHAR(32) DEFAULT NULL COMMENT '服务报告ID',
  order_id VARCHAR(32) DEFAULT NULL COMMENT '订单ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  field_name VARCHAR(64) NOT NULL COMMENT '建议变更字段',
  old_value VARCHAR(512) DEFAULT NULL COMMENT '变更前值',
  new_value VARCHAR(512) NOT NULL COMMENT '变更后值',
  source_type VARCHAR(32) NOT NULL COMMENT '来源类型',
  source_id VARCHAR(32) DEFAULT NULL COMMENT '来源ID',
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  created_by VARCHAR(32) DEFAULT NULL COMMENT '创建人用户ID',
  reviewer_id VARCHAR(32) DEFAULT NULL COMMENT '审核人用户ID',
  reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
  review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (review_task_id),
  KEY idx_health_review_elder_status (elder_id, review_status),
  KEY idx_health_review_report (report_id),
  KEY idx_health_review_order (order_id),
  CONSTRAINT fk_health_review_report FOREIGN KEY (report_id) REFERENCES service_report (report_id),
  CONSTRAINT fk_health_review_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_health_review_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_health_review_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id),
  CONSTRAINT fk_health_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_health_review_source_type CHECK (source_type IN ('REPORT_ACK','SERVICE_RECORD','MANUAL')),
  CONSTRAINT ck_health_review_status CHECK (review_status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康信息审核任务';
