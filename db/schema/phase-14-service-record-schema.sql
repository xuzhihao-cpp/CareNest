USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS care_service_record (
  record_id VARCHAR(32) NOT NULL COMMENT '服务记录ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  task_id VARCHAR(32) DEFAULT NULL COMMENT '护理任务ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT '护理人员用户ID',
  start_time DATETIME NOT NULL COMMENT '服务开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '服务结束时间',
  content VARCHAR(1000) NOT NULL COMMENT '服务内容',
  nursing_advice VARCHAR(1000) DEFAULT NULL COMMENT '护理建议',
  abnormal_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否异常',
  created_by VARCHAR(32) DEFAULT NULL COMMENT '创建人用户ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (record_id),
  KEY idx_care_service_record_order (order_id),
  KEY idx_care_service_record_task (task_id),
  KEY idx_care_service_record_nurse (nurse_id),
  CONSTRAINT fk_care_service_record_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_care_service_record_task FOREIGN KEY (task_id) REFERENCES nurse_task (task_id),
  CONSTRAINT fk_care_service_record_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='护理服务记录';

CREATE TABLE IF NOT EXISTS vital_sign_record (
  vital_id VARCHAR(32) NOT NULL COMMENT '生命体征记录ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  task_id VARCHAR(32) DEFAULT NULL COMMENT '护理任务ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT '护理人员用户ID',
  measured_at DATETIME NOT NULL COMMENT '测量时间',
  temperature DECIMAL(4,1) DEFAULT NULL COMMENT '体温',
  pulse INT DEFAULT NULL COMMENT '脉搏',
  breath_rate INT DEFAULT NULL COMMENT '呼吸频率',
  systolic_pressure INT DEFAULT NULL COMMENT '收缩压',
  diastolic_pressure INT DEFAULT NULL COMMENT '舒张压',
  blood_oxygen INT DEFAULT NULL COMMENT '血氧',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (vital_id),
  KEY idx_vital_sign_order (order_id),
  KEY idx_vital_sign_task (task_id),
  KEY idx_vital_sign_nurse (nurse_id),
  CONSTRAINT fk_vital_sign_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_vital_sign_task FOREIGN KEY (task_id) REFERENCES nurse_task (task_id),
  CONSTRAINT fk_vital_sign_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生命体征记录';
