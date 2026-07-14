USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS file_asset (
  file_id VARCHAR(32) NOT NULL COMMENT '文件ID',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  mime_type VARCHAR(128) NOT NULL COMMENT 'MIME类型',
  file_size BIGINT NOT NULL COMMENT '文件大小',
  storage_bucket VARCHAR(64) NOT NULL COMMENT '存储桶',
  object_key VARCHAR(255) NOT NULL COMMENT '对象存储Key',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  uploaded_by VARCHAR(32) NOT NULL COMMENT '上传人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (file_id),
  KEY idx_file_asset_uploaded_by (uploaded_by),
  KEY idx_file_asset_owner_status (uploaded_by, audit_status, created_at),
  CONSTRAINT ck_file_asset_audit_status CHECK (audit_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件资产';

CREATE TABLE IF NOT EXISTS medical_file (
  medical_file_id VARCHAR(32) NOT NULL COMMENT '病历资料ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  file_id VARCHAR(32) NOT NULL COMMENT '文件ID',
  file_type VARCHAR(32) NOT NULL COMMENT '文件类型',
  title VARCHAR(128) NOT NULL COMMENT '标题',
  occurred_at DATE DEFAULT NULL COMMENT '发生日期',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  review_comment VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
  uploader_id VARCHAR(32) NOT NULL COMMENT '上传人',
  reviewer_id VARCHAR(32) DEFAULT NULL COMMENT '审核人',
  reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (medical_file_id),
  UNIQUE KEY uk_medical_file_file (file_id),
  KEY idx_medical_file_elder_status (elder_id, audit_status),
  CONSTRAINT fk_medical_file_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_medical_file_asset FOREIGN KEY (file_id) REFERENCES file_asset (file_id),
  CONSTRAINT ck_medical_file_audit_status CHECK (audit_status IN ('PENDING','APPROVED','REJECTED','NEED_MORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='病历资料';
