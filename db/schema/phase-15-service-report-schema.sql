USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS service_report (
  report_id VARCHAR(32) NOT NULL COMMENT '服务报告ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  report_status VARCHAR(32) NOT NULL DEFAULT 'WAIT_CONFIRM' COMMENT '报告状态',
  summary VARCHAR(1000) NOT NULL COMMENT '报告摘要',
  nursing_advice VARCHAR(1000) DEFAULT NULL COMMENT '护理建议',
  generated_by VARCHAR(32) DEFAULT NULL COMMENT '生成人用户ID',
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  confirmed_at DATETIME DEFAULT NULL COMMENT '确认时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (report_id),
  UNIQUE KEY uk_service_report_order (order_id),
  KEY idx_service_report_status (report_status),
  CONSTRAINT fk_service_report_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_service_report_generated_by FOREIGN KEY (generated_by) REFERENCES sys_user (user_id),
  CONSTRAINT ck_service_report_status CHECK (report_status IN ('DRAFT','WAIT_CONFIRM','CONFIRMED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务报告';

CREATE TABLE IF NOT EXISTS service_report_item (
  item_id VARCHAR(32) NOT NULL COMMENT '报告明细ID',
  report_id VARCHAR(32) NOT NULL COMMENT '服务报告ID',
  item_type VARCHAR(32) NOT NULL COMMENT '明细类型',
  item_title VARCHAR(128) NOT NULL COMMENT '明细标题',
  item_content VARCHAR(1000) NOT NULL COMMENT '明细内容',
  source_id VARCHAR(32) DEFAULT NULL COMMENT '来源记录ID',
  sort INT NOT NULL DEFAULT 1 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (item_id),
  KEY idx_service_report_item_report (report_id),
  CONSTRAINT fk_service_report_item_report FOREIGN KEY (report_id) REFERENCES service_report (report_id),
  CONSTRAINT ck_service_report_item_type CHECK (item_type IN ('SERVICE_RECORD','VITAL_SIGN','NURSING_ADVICE','RISK_NOTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务报告明细';
