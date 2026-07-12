USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS authorization_scope (
  scope_code VARCHAR(64) NOT NULL COMMENT '授权范围编码',
  scope_name VARCHAR(64) NOT NULL COMMENT '授权范围名称',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  sort INT NOT NULL DEFAULT 1 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (scope_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='授权范围';

CREATE TABLE IF NOT EXISTS elder_family_binding (
  binding_id VARCHAR(32) NOT NULL COMMENT '绑定ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  family_id VARCHAR(32) NOT NULL COMMENT '家属ID',
  binding_status VARCHAR(32) NOT NULL COMMENT '绑定状态',
  scope_codes JSON NOT NULL COMMENT '授权范围编码列表',
  pending_scope_codes JSON NULL COMMENT '待长辈确认的授权范围',
  scope_update_status VARCHAR(32) NULL COMMENT '授权变更状态',
  relation_type VARCHAR(32) DEFAULT NULL COMMENT '亲属关系',
  inviter_user_id VARCHAR(32) DEFAULT NULL COMMENT '邀请人用户ID',
  approver_user_id VARCHAR(32) DEFAULT NULL COMMENT '确认人用户ID',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (binding_id),
  KEY idx_binding_elder (elder_id),
  KEY idx_binding_family (family_id),
  KEY idx_binding_status (binding_status),
  CONSTRAINT ck_binding_status CHECK (binding_status IN ('PENDING','ACTIVE','REJECTED','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长辈家属绑定';
