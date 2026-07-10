DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS login_session;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS service_address;
DROP TABLE IF EXISTS health_archive_change_log;
DROP TABLE IF EXISTS elder_contact;
DROP TABLE IF EXISTS elder_family_binding;
DROP TABLE IF EXISTS elder_profile;
DROP TABLE IF EXISTS authorization_scope;

CREATE TABLE sys_role (
  role_id VARCHAR(32) NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id),
  CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_user (
  user_id VARCHAR(32) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  account_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE user_role (
  user_id VARCHAR(32) NOT NULL,
  role_id VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE login_session (
  session_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expire_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (session_id)
);

CREATE TABLE sys_permission (
  permission_id VARCHAR(32) NOT NULL,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_group VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (permission_id),
  CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
);

CREATE TABLE role_permission (
  role_id VARCHAR(32) NOT NULL,
  permission_id VARCHAR(32) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE operation_log (
  log_id VARCHAR(32) NOT NULL,
  operator_id VARCHAR(32),
  role_code VARCHAR(64),
  operation_type VARCHAR(64) NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id VARCHAR(64),
  before_value VARCHAR(4000),
  after_value VARCHAR(4000),
  trace_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id)
);

CREATE TABLE authorization_scope (
  scope_code VARCHAR(64) NOT NULL,
  scope_name VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  sort INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scope_code)
);

CREATE TABLE elder_profile (
  elder_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32),
  elder_name VARCHAR(64) NOT NULL,
  gender VARCHAR(16),
  birth_date DATE,
  care_level VARCHAR(32),
  emergency_contact_name VARCHAR(64),
  emergency_contact_phone VARCHAR(32),
  health_summary VARCHAR(512),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (elder_id)
);

CREATE TABLE elder_contact (
  contact_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  relation_type VARCHAR(32),
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (contact_id)
);

CREATE TABLE health_archive_change_log (
  change_log_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  changed_by VARCHAR(32),
  change_type VARCHAR(64) NOT NULL,
  before_value VARCHAR(4000),
  after_value VARCHAR(4000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (change_log_id)
);

CREATE TABLE elder_family_binding (
  binding_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  family_id VARCHAR(32) NOT NULL,
  binding_status VARCHAR(32) NOT NULL,
  scope_codes VARCHAR(4000) NOT NULL,
  relation_type VARCHAR(32),
  inviter_user_id VARCHAR(32),
  approver_user_id VARCHAR(32),
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (binding_id)
);

CREATE TABLE service_address (
  address_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  family_id VARCHAR(32) NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  province_code VARCHAR(32) NOT NULL,
  city_code VARCHAR(32) NOT NULL,
  region_code VARCHAR(32) NOT NULL,
  detail_address VARCHAR(255) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (address_id)
);
