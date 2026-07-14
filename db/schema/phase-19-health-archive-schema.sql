USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS health_archive (
  archive_id VARCHAR(32) NOT NULL COMMENT '健康档案ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  archive_version INT NOT NULL DEFAULT 1 COMMENT '档案版本',
  care_summary VARCHAR(512) DEFAULT NULL COMMENT '照护摘要',
  updated_by VARCHAR(32) DEFAULT NULL COMMENT '更新人用户ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (archive_id),
  UNIQUE KEY uk_health_archive_elder (elder_id),
  CONSTRAINT fk_health_archive_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康档案归档主表';

CREATE TABLE IF NOT EXISTS chronic_disease (
  disease_id VARCHAR(32) NOT NULL COMMENT '慢病ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  disease_name VARCHAR(64) NOT NULL COMMENT '疾病名称',
  disease_status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '疾病状态',
  diagnosed_at DATE DEFAULT NULL COMMENT '确诊日期',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (disease_id),
  KEY idx_chronic_disease_elder (elder_id),
  UNIQUE KEY uk_chronic_disease_elder_name (elder_id, disease_name),
  CONSTRAINT fk_chronic_disease_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='慢病记录';

CREATE TABLE IF NOT EXISTS medication_plan (
  medication_id VARCHAR(32) NOT NULL COMMENT '用药ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  medication_name VARCHAR(64) NOT NULL COMMENT '药品名称',
  dosage VARCHAR(64) DEFAULT NULL COMMENT '剂量',
  frequency VARCHAR(64) DEFAULT NULL COMMENT '频次',
  time_points JSON DEFAULT NULL COMMENT '服药时间点',
  start_date DATE DEFAULT NULL COMMENT '开始日期',
  end_date DATE DEFAULT NULL COMMENT '结束日期',
  medication_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用药状态',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (medication_id),
  KEY idx_medication_plan_elder (elder_id),
  UNIQUE KEY uk_medication_plan_elder_name (elder_id, medication_name),
  CONSTRAINT fk_medication_plan_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用药计划';

CREATE TABLE IF NOT EXISTS allergy_record (
  allergy_id VARCHAR(32) NOT NULL COMMENT '过敏ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  allergen VARCHAR(64) NOT NULL COMMENT '过敏原',
  reaction VARCHAR(128) DEFAULT NULL COMMENT '反应',
  severity VARCHAR(32) DEFAULT NULL COMMENT '严重程度',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (allergy_id),
  KEY idx_allergy_record_elder (elder_id),
  UNIQUE KEY uk_allergy_record_elder_name (elder_id, allergen),
  CONSTRAINT fk_allergy_record_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='过敏记录';

CREATE TABLE IF NOT EXISTS risk_tag (
  risk_tag_id VARCHAR(32) NOT NULL COMMENT '风险标签ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  tag_code VARCHAR(64) NOT NULL COMMENT '风险标签稳定代码',
  tag_name VARCHAR(64) NOT NULL COMMENT '标签名称',
  risk_level VARCHAR(32) DEFAULT NULL COMMENT '风险等级',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (risk_tag_id),
  KEY idx_risk_tag_elder (elder_id),
  UNIQUE KEY uk_risk_tag_elder_code (elder_id, tag_code),
  CONSTRAINT fk_risk_tag_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风险标签';

CREATE TABLE IF NOT EXISTS care_plan (
  care_plan_id VARCHAR(32) NOT NULL COMMENT '照护计划ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  plan_content TEXT NOT NULL COMMENT '照护计划内容',
  plan_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '计划状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (care_plan_id),
  KEY idx_care_plan_elder (elder_id),
  UNIQUE KEY uk_care_plan_elder (elder_id),
  CONSTRAINT fk_care_plan_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照护计划';
