CREATE TABLE service_item (
  service_id VARCHAR(32) PRIMARY KEY,
  service_name VARCHAR(128),
  service_status VARCHAR(32)
);
CREATE TABLE nursing_order (
  order_id VARCHAR(32) PRIMARY KEY,
  service_id VARCHAR(32),
  elder_id VARCHAR(32),
  family_id VARCHAR(32),
  order_status VARCHAR(32)
);
CREATE TABLE nurse_task (
  task_id VARCHAR(32) PRIMARY KEY,
  order_id VARCHAR(32),
  nurse_id VARCHAR(32),
  task_status VARCHAR(32)
);
CREATE TABLE file_asset (
  file_id VARCHAR(32) PRIMARY KEY,
  uploaded_by VARCHAR(32),
  mime_type VARCHAR(128)
);
CREATE TABLE elder_family_binding (
  binding_id VARCHAR(32) PRIMARY KEY,
  elder_id VARCHAR(32),
  family_id VARCHAR(32),
  binding_status VARCHAR(32),
  scope_codes VARCHAR(500)
);
CREATE TABLE sys_role (
  role_id VARCHAR(32) PRIMARY KEY,
  role_code VARCHAR(64),
  enabled TINYINT
);
CREATE TABLE user_role (
  user_id VARCHAR(32),
  role_id VARCHAR(32),
  PRIMARY KEY (user_id, role_id)
);
CREATE TABLE sys_permission (
  permission_id VARCHAR(32) PRIMARY KEY,
  permission_code VARCHAR(128),
  enabled TINYINT
);
CREATE TABLE role_permission (
  role_id VARCHAR(32),
  permission_id VARCHAR(32),
  PRIMARY KEY (role_id, permission_id)
);
CREATE TABLE operation_log (
  log_id VARCHAR(32) PRIMARY KEY,
  operator_id VARCHAR(32),
  role_code VARCHAR(64),
  operation_type VARCHAR(64),
  biz_type VARCHAR(64),
  biz_id VARCHAR(64),
  before_value VARCHAR(2000),
  after_value VARCHAR(2000),
  trace_id VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE care_metric_config (
  config_id VARCHAR(32) PRIMARY KEY,
  service_id VARCHAR(32),
  config_version INT,
  config_status VARCHAR(32),
  created_by VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (service_id, config_version)
);
CREATE TABLE care_metric_item (
  metric_item_id VARCHAR(32) PRIMARY KEY,
  config_id VARCHAR(32),
  service_id VARCHAR(32),
  metric_code VARCHAR(64),
  metric_name VARCHAR(128),
  metric_type VARCHAR(32),
  required TINYINT,
  evidence_type VARCHAR(32),
  expected_action VARCHAR(500),
  score_weight DECIMAL(5,2),
  description VARCHAR(500),
  sort INT,
  enabled TINYINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (config_id, metric_code)
);
CREATE TABLE metric_score_rule (
  score_rule_id VARCHAR(32) PRIMARY KEY,
  metric_item_id VARCHAR(32),
  rule_type VARCHAR(32),
  score_delta DECIMAL(6,2),
  description VARCHAR(500),
  enabled TINYINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_metric_checklist (
  checklist_id VARCHAR(32) PRIMARY KEY,
  order_id VARCHAR(32) UNIQUE,
  service_id VARCHAR(32),
  config_id VARCHAR(32),
  config_version INT,
  generated_by VARCHAR(32),
  generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_metric_item (
  order_metric_item_id VARCHAR(32) PRIMARY KEY,
  checklist_id VARCHAR(32),
  order_id VARCHAR(32),
  metric_item_id VARCHAR(32),
  metric_code VARCHAR(64),
  metric_name VARCHAR(128),
  required TINYINT,
  evidence_type VARCHAR(32),
  score_weight DECIMAL(5,2),
  metric_status VARCHAR(32),
  submitted_at TIMESTAMP,
  reviewed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (order_id, metric_code)
);
CREATE TABLE care_service_evidence (
  evidence_id VARCHAR(32) PRIMARY KEY,
  order_id VARCHAR(32),
  task_id VARCHAR(32),
  order_metric_item_id VARCHAR(32),
  nurse_id VARCHAR(32),
  file_id VARCHAR(32),
  evidence_type VARCHAR(32),
  description VARCHAR(500),
  audit_status VARCHAR(32),
  submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  reviewed_by VARCHAR(32),
  reviewed_at TIMESTAMP,
  review_comment VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE evidence_review_record (
  review_record_id VARCHAR(32) PRIMARY KEY,
  evidence_id VARCHAR(32),
  from_status VARCHAR(32),
  to_status VARCHAR(32),
  review_comment VARCHAR(500),
  reviewer_id VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE nurse_metric_record (
  metric_record_id VARCHAR(32) PRIMARY KEY,
  nurse_id VARCHAR(32),
  order_id VARCHAR(32),
  order_metric_item_id VARCHAR(32),
  metric_status VARCHAR(32),
  score_delta DECIMAL(6,2),
  source_type VARCHAR(64),
  source_id VARCHAR(32),
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE metric_exception_proof (
  proof_id VARCHAR(32) PRIMARY KEY,
  order_metric_item_id VARCHAR(32),
  evidence_id VARCHAR(32),
  nurse_id VARCHAR(32),
  reason_type VARCHAR(32),
  reason VARCHAR(500),
  proof_status VARCHAR(32),
  review_comment VARCHAR(500),
  reviewed_by VARCHAR(32),
  reviewed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
