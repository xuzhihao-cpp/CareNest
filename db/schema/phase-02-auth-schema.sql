USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  role_id VARCHAR(32) NOT NULL COMMENT '角色ID',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS sys_user (
  user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  display_name VARCHAR(64) NOT NULL COMMENT '展示名称',
  phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  account_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_phone (phone),
  CONSTRAINT ck_sys_user_account_status CHECK (account_status IN ('ENABLED','DISABLED','LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS user_role (
  user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
  role_id VARCHAR(32) NOT NULL COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关系';

CREATE TABLE IF NOT EXISTS login_session (
  session_id VARCHAR(32) NOT NULL COMMENT '会话ID',
  user_id VARCHAR(32) NOT NULL COMMENT '用户ID',
  token_hash VARCHAR(255) NOT NULL COMMENT 'Token哈希',
  expire_at DATETIME NOT NULL COMMENT '过期时间',
  revoked_at DATETIME DEFAULT NULL COMMENT '撤销时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (session_id),
  KEY idx_login_session_user (user_id),
  KEY idx_login_session_expire_at (expire_at),
  CONSTRAINT fk_login_session_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话';
