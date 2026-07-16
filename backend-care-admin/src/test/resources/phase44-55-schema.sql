CREATE TABLE sys_user (
  user_id VARCHAR(32) PRIMARY KEY, username VARCHAR(64), account_status VARCHAR(32)
);
CREATE TABLE sys_role (
  role_id VARCHAR(32) PRIMARY KEY, role_code VARCHAR(64), enabled TINYINT
);
CREATE TABLE user_role (user_id VARCHAR(32), role_id VARCHAR(32));
CREATE TABLE sys_permission (
  permission_id VARCHAR(32) PRIMARY KEY, permission_code VARCHAR(128), enabled TINYINT
);
CREATE TABLE role_permission (role_id VARCHAR(32), permission_id VARCHAR(32));
CREATE TABLE elder_profile (elder_id VARCHAR(32) PRIMARY KEY, user_id VARCHAR(32));
CREATE TABLE elder_family_binding (
  binding_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), family_id VARCHAR(32),
  binding_status VARCHAR(32), scope_codes VARCHAR(500)
);
CREATE TABLE service_item (
  service_id VARCHAR(32) PRIMARY KEY, service_name VARCHAR(128), service_status VARCHAR(32)
);
CREATE TABLE nursing_order (
  order_id VARCHAR(32) PRIMARY KEY, service_id VARCHAR(32), elder_id VARCHAR(32),
  family_id VARCHAR(32), order_status VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE nurse_task (
  task_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32), nurse_id VARCHAR(32),
  task_status VARCHAR(32), completed_at TIMESTAMP
);
CREATE TABLE service_report (
  report_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32)
);
CREATE TABLE file_asset (
  file_id VARCHAR(32) PRIMARY KEY, uploaded_by VARCHAR(32), mime_type VARCHAR(128)
);
CREATE TABLE operation_log (
  log_id VARCHAR(32) PRIMARY KEY, operator_id VARCHAR(32), role_code VARCHAR(64),
  operation_type VARCHAR(64), biz_type VARCHAR(64), biz_id VARCHAR(64),
  before_value VARCHAR(2000), after_value VARCHAR(2000), trace_id VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE customer_service_ticket (
  ticket_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), requester_id VARCHAR(32),
  assigned_to VARCHAR(32), category VARCHAR(64), priority VARCHAR(32),
  ticket_status VARCHAR(32), description VARCHAR(500), source_type VARCHAR(32),
  resolved_at TIMESTAMP, closed_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE follow_up_record (
  follow_up_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), ticket_id VARCHAR(32),
  order_id VARCHAR(32), follow_up_type VARCHAR(32), content VARCHAR(1000),
  next_follow_up_at TIMESTAMP, need_reminder TINYINT, created_reminder_task_id VARCHAR(32),
  created_by VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE reminder_task (
  task_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), reminder_type VARCHAR(32),
  title VARCHAR(128), content VARCHAR(500), scheduled_at TIMESTAMP,
  reminder_status VARCHAR(32), source_type VARCHAR(64), source_id VARCHAR(32),
  created_by VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE reminder_record (
  record_id VARCHAR(32) PRIMARY KEY, task_id VARCHAR(32), elder_id VARCHAR(32),
  result VARCHAR(32), operated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE review (
  review_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32), report_id VARCHAR(32),
  reviewer_id VARCHAR(32), reviewer_role VARCHAR(32), rating INT, satisfaction INT,
  content VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE complaint (
  complaint_id VARCHAR(32) PRIMARY KEY, review_id VARCHAR(32), order_id VARCHAR(32),
  complainant_id VARCHAR(32), complaint_status VARCHAR(32), content VARCHAR(1000),
  handled_by VARCHAR(32), handle_result VARCHAR(1000), handled_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE nurse_profile (nurse_id VARCHAR(32) PRIMARY KEY);
CREATE TABLE nurse_score (
  nurse_id VARCHAR(32) PRIMARY KEY, total_score DECIMAL(6,2), service_count INT,
  positive_rate DECIMAL(6,2), complaint_count INT, last_service_at TIMESTAMP,
  updated_by VARCHAR(32)
);
CREATE TABLE nurse_score_change_log (
  change_log_id VARCHAR(32) PRIMARY KEY, nurse_id VARCHAR(32), source_event_type VARCHAR(64),
  source_event_id VARCHAR(32), before_score DECIMAL(6,2), after_score DECIMAL(6,2),
  score_delta DECIMAL(6,2), reason VARCHAR(500), changed_by VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE metric_score_rule (
  score_rule_id VARCHAR(32) PRIMARY KEY, metric_item_id VARCHAR(32),
  rule_type VARCHAR(32), score_delta DECIMAL(6,2), enabled TINYINT
);
CREATE TABLE order_metric_item (
  order_metric_item_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32),
  score_weight DECIMAL(5,2), metric_status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE metric_exception_proof (
  proof_id VARCHAR(32) PRIMARY KEY, order_metric_item_id VARCHAR(32), nurse_id VARCHAR(32),
  proof_status VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE nurse_appeal (
  appeal_id VARCHAR(32) PRIMARY KEY, nurse_id VARCHAR(32), target_type VARCHAR(32),
  target_id VARCHAR(32), reason VARCHAR(1000), file_ids VARCHAR(1000),
  appeal_status VARCHAR(32), score_adjustment DECIMAL(6,2), review_comment VARCHAR(500),
  reviewed_by VARCHAR(32), reviewed_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE training_article (
  article_id VARCHAR(32) PRIMARY KEY, title VARCHAR(128), content_summary VARCHAR(500),
  content_url VARCHAR(255), service_id VARCHAR(32), required_reading TINYINT,
  article_status VARCHAR(32), created_by VARCHAR(32), published_at TIMESTAMP,
  offline_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE article_tag (
  tag_id VARCHAR(32) PRIMARY KEY, article_id VARCHAR(32), tag_code VARCHAR(128), tag_name VARCHAR(128)
);
CREATE TABLE article_recommend_rule (
  rule_id VARCHAR(32) PRIMARY KEY, article_id VARCHAR(32), service_id VARCHAR(32),
  enabled TINYINT, sort INT
);
CREATE TABLE nurse_article_reading (
  reading_id VARCHAR(32) PRIMARY KEY, article_id VARCHAR(32), nurse_id VARCHAR(32),
  reading_status VARCHAR(32), read_at TIMESTAMP
);
CREATE TABLE risk_tag (
  risk_tag_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), tag_code VARCHAR(64)
);
CREATE TABLE care_service_evidence (
  evidence_id VARCHAR(32) PRIMARY KEY, audit_status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE bug_list (
  bug_id VARCHAR(32) PRIMARY KEY, severity VARCHAR(32), bug_status VARCHAR(32)
);
