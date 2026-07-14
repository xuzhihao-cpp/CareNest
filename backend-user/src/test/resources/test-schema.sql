DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS care_report_ack;
DROP TABLE IF EXISTS health_info_review_task;
DROP TABLE IF EXISTS order_status_log;
DROP TABLE IF EXISTS service_report;
DROP TABLE IF EXISTS nurse_task;
DROP TABLE IF EXISTS nursing_order;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS login_session;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS service_address;
DROP TABLE IF EXISTS medical_file;
DROP TABLE IF EXISTS file_asset;
DROP TABLE IF EXISTS care_plan;
DROP TABLE IF EXISTS risk_tag;
DROP TABLE IF EXISTS allergy_record;
DROP TABLE IF EXISTS medication_plan;
DROP TABLE IF EXISTS chronic_disease;
DROP TABLE IF EXISTS health_archive;
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

CREATE TABLE file_asset (
  file_id VARCHAR(32) NOT NULL PRIMARY KEY,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  file_size BIGINT NOT NULL,
  storage_bucket VARCHAR(64) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  uploaded_by VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medical_file (
  medical_file_id VARCHAR(32) NOT NULL PRIMARY KEY,
  elder_id VARCHAR(32) NOT NULL,
  file_id VARCHAR(32) NOT NULL UNIQUE,
  file_type VARCHAR(32) NOT NULL,
  title VARCHAR(128) NOT NULL,
  occurred_at DATE,
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  review_comment VARCHAR(255),
  uploader_id VARCHAR(32) NOT NULL,
  reviewer_id VARCHAR(32),
  reviewed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE health_archive (
  archive_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  archive_version INT NOT NULL DEFAULT 1,
  care_summary VARCHAR(512),
  updated_by VARCHAR(32),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (archive_id),
  CONSTRAINT uk_health_archive_elder UNIQUE (elder_id)
);

CREATE TABLE chronic_disease (
  disease_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  disease_name VARCHAR(64) NOT NULL,
  disease_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  diagnosed_at DATE,
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (disease_id),
  CONSTRAINT uk_chronic_disease_elder_name UNIQUE (elder_id, disease_name)
);

CREATE TABLE medication_plan (
  medication_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  medication_name VARCHAR(64) NOT NULL,
  dosage VARCHAR(64),
  frequency VARCHAR(64) NOT NULL,
  time_points VARCHAR(1000),
  start_date DATE NOT NULL,
  end_date DATE,
  medication_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (medication_id),
  CONSTRAINT uk_medication_plan_elder_name UNIQUE (elder_id, medication_name)
);

CREATE TABLE allergy_record (
  allergy_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  allergen VARCHAR(64) NOT NULL,
  reaction VARCHAR(128),
  severity VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (allergy_id),
  CONSTRAINT uk_allergy_record_elder_name UNIQUE (elder_id, allergen)
);

CREATE TABLE risk_tag (
  risk_tag_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  tag_code VARCHAR(64) NOT NULL,
  tag_name VARCHAR(64) NOT NULL,
  risk_level VARCHAR(32),
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (risk_tag_id),
  CONSTRAINT uk_risk_tag_elder_code UNIQUE (elder_id, tag_code)
);

CREATE TABLE care_plan (
  care_plan_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  plan_content VARCHAR(4000) NOT NULL,
  plan_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (care_plan_id),
  CONSTRAINT uk_care_plan_elder UNIQUE (elder_id)
);

CREATE TABLE elder_family_binding (
  binding_id VARCHAR(32) NOT NULL,
  elder_id VARCHAR(32) NOT NULL,
  family_id VARCHAR(32) NOT NULL,
  binding_status VARCHAR(32) NOT NULL,
  scope_codes VARCHAR(4000) NOT NULL,
  pending_scope_codes VARCHAR(4000),
  scope_update_status VARCHAR(32),
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

CREATE TABLE nursing_order (
  order_id VARCHAR(32) NOT NULL PRIMARY KEY,
  elder_id VARCHAR(32) NOT NULL,
  family_id VARCHAR(32) NOT NULL,
  service_id VARCHAR(32) NOT NULL,
  address_id VARCHAR(32) NOT NULL,
  order_status VARCHAR(32) NOT NULL,
  scheduled_start_at TIMESTAMP NOT NULL,
  scheduled_end_at TIMESTAMP,
  service_price_cent INT NOT NULL DEFAULT 0,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  created_by VARCHAR(32),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE nurse_task (
  task_id VARCHAR(32) NOT NULL PRIMARY KEY,
  order_id VARCHAR(32) NOT NULL UNIQUE,
  nurse_id VARCHAR(32) NOT NULL,
  task_status VARCHAR(32) NOT NULL
);

CREATE TABLE service_report (
  report_id VARCHAR(32) NOT NULL PRIMARY KEY,
  order_id VARCHAR(32) NOT NULL UNIQUE,
  report_status VARCHAR(32) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  nursing_advice VARCHAR(1000),
  generated_by VARCHAR(32),
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE care_report_ack (
  ack_id VARCHAR(32) NOT NULL PRIMARY KEY,
  report_id VARCHAR(32) NOT NULL UNIQUE,
  order_id VARCHAR(32) NOT NULL,
  ack_user_id VARCHAR(32) NOT NULL,
  ack_role VARCHAR(32) NOT NULL,
  ack_result VARCHAR(32) NOT NULL,
  satisfaction INT,
  remark VARCHAR(255),
  accepted_suggestion_ids VARCHAR(4000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE health_info_review_task (
  review_task_id VARCHAR(32) NOT NULL PRIMARY KEY,
  report_id VARCHAR(32),
  order_id VARCHAR(32),
  elder_id VARCHAR(32) NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  old_value VARCHAR(512),
  new_value VARCHAR(512) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(32),
  review_status VARCHAR(32) NOT NULL,
  created_by VARCHAR(32),
  reviewer_id VARCHAR(32),
  reviewed_at TIMESTAMP,
  review_remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_status_log (
  status_log_id VARCHAR(32) NOT NULL PRIMARY KEY,
  order_id VARCHAR(32) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  changed_by VARCHAR(32),
  change_reason VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
