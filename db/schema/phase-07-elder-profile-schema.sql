USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS elder_profile (
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  user_id VARCHAR(32) DEFAULT NULL COMMENT '关联用户ID',
  elder_name VARCHAR(64) NOT NULL COMMENT '长辈姓名',
  gender VARCHAR(16) DEFAULT NULL COMMENT '性别',
  birth_date DATE DEFAULT NULL COMMENT '出生日期',
  care_level VARCHAR(32) DEFAULT NULL COMMENT '照护等级',
  emergency_contact_name VARCHAR(64) DEFAULT NULL COMMENT '紧急联系人姓名',
  emergency_contact_phone VARCHAR(32) DEFAULT NULL COMMENT '紧急联系人电话',
  health_summary VARCHAR(512) DEFAULT NULL COMMENT '健康摘要',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (elder_id),
  KEY idx_elder_profile_user (user_id),
  CONSTRAINT fk_elder_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长辈基础档案';

CREATE TABLE IF NOT EXISTS elder_contact (
  contact_id VARCHAR(32) NOT NULL COMMENT '联系人ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  contact_name VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  contact_phone VARCHAR(32) NOT NULL COMMENT '联系人电话',
  relation_type VARCHAR(32) DEFAULT NULL COMMENT '关系类型',
  is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主要联系人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (contact_id),
  KEY idx_elder_contact_elder (elder_id),
  CONSTRAINT fk_elder_contact_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长辈联系人';

CREATE TABLE IF NOT EXISTS health_archive_change_log (
  change_log_id VARCHAR(32) NOT NULL COMMENT '健康档案变更日志ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  changed_by VARCHAR(32) DEFAULT NULL COMMENT '变更人用户ID',
  change_type VARCHAR(64) NOT NULL COMMENT '变更类型',
  before_value JSON DEFAULT NULL COMMENT '变更前数据',
  after_value JSON DEFAULT NULL COMMENT '变更后数据',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (change_log_id),
  KEY idx_health_archive_change_elder (elder_id),
  CONSTRAINT fk_health_archive_change_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康档案变更日志';
