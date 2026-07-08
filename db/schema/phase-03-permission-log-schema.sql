USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
  permission_id VARCHAR(32) NOT NULL COMMENT '权限ID',
  permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
  permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
  permission_group VARCHAR(64) NOT NULL COMMENT '权限分组',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (permission_id),
  UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统权限';

CREATE TABLE IF NOT EXISTS role_permission (
  role_id VARCHAR(32) NOT NULL COMMENT '角色ID',
  permission_id VARCHAR(32) NOT NULL COMMENT '权限ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关系';

CREATE TABLE IF NOT EXISTS operation_log (
  log_id VARCHAR(32) NOT NULL COMMENT '操作日志ID',
  operator_id VARCHAR(32) DEFAULT NULL COMMENT '操作人用户ID',
  role_code VARCHAR(64) DEFAULT NULL COMMENT '操作角色',
  operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  biz_id VARCHAR(64) DEFAULT NULL COMMENT '业务ID',
  before_value JSON DEFAULT NULL COMMENT '变更前数据',
  after_value JSON DEFAULT NULL COMMENT '变更后数据',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT '请求追踪ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (log_id),
  KEY idx_operation_log_operator (operator_id),
  KEY idx_operation_log_biz (biz_type, biz_id),
  KEY idx_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';
