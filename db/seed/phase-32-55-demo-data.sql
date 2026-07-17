USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO sys_permission (permission_id, permission_code, permission_name, permission_group, enabled) VALUES
('perm_reminder_view', 'REMINDER_VIEW', '查看提醒', 'reminder', 1),
('perm_reminder_update', 'REMINDER_UPDATE', '处理提醒', 'reminder', 1),
('perm_reminder_record_view', 'REMINDER_RECORD_VIEW', '查看提醒记录', 'reminder', 1),
('perm_care_metric_config', 'CARE_METRIC_CONFIG_MANAGE', '管理护理指标配置', 'care_metric', 1),
('perm_evidence_review', 'CARE_EVIDENCE_REVIEW', '审核护理留档', 'care_metric', 1),
('perm_ai_session_review', 'AI_SESSION_REVIEW', '查看AI会话日志', 'ai', 1),
('perm_complaint_handle', 'COMPLAINT_HANDLE', '处理投诉', 'complaint', 1),
('perm_nurse_appeal_review', 'NURSE_APPEAL_REVIEW', '审核护理申诉', 'appeal', 1),
('perm_training_article_manage', 'TRAINING_ARTICLE_MANAGE', '管理培训文章', 'training', 1),
('perm_follow_up_manage', 'FOLLOW_UP_MANAGE', '管理随访记录', 'follow_up', 1),
('perm_dashboard_basic_view', 'DASHBOARD_BASIC_VIEW', '查看基础数据看板', 'dashboard', 1),
('perm_dashboard_quality_view', 'DASHBOARD_QUALITY_VIEW', '查看质量数据看板', 'dashboard', 1),
('perm_demo_data_manage', 'DEMO_DATA_MANAGE', '管理演示数据', 'delivery', 1)
ON DUPLICATE KEY UPDATE
  permission_code = VALUES(permission_code),
  permission_name = VALUES(permission_name),
  permission_group = VALUES(permission_group),
  enabled = VALUES(enabled);

INSERT INTO role_permission (role_id, permission_id, sort) VALUES
('role_elder', 'perm_reminder_view', 3201),
('role_elder', 'perm_reminder_update', 3202),
('role_family', 'perm_reminder_record_view', 3301),
('role_admin', 'perm_reminder_record_view', 3302),
('role_customer_service', 'perm_reminder_record_view', 3303),
('role_admin', 'perm_care_metric_config', 3401),
('role_customer_service', 'perm_care_metric_config', 3402),
('role_admin', 'perm_evidence_review', 3701),
('role_customer_service', 'perm_evidence_review', 3702),
('role_admin', 'perm_ai_session_review', 4201),
('role_customer_service', 'perm_ai_session_review', 4202),
('role_admin', 'perm_cs_ticket_handle', 4301),
('role_customer_service', 'perm_complaint_handle', 4501),
('role_admin', 'perm_complaint_handle', 4502),
('role_admin', 'perm_nurse_appeal_review', 4601),
('role_customer_service', 'perm_nurse_appeal_review', 4602),
('role_admin', 'perm_training_article_manage', 4901),
('role_customer_service', 'perm_training_article_manage', 4902),
('role_admin', 'perm_follow_up_manage', 5101),
('role_customer_service', 'perm_follow_up_manage', 5102),
('role_admin', 'perm_dashboard_basic_view', 5201),
('role_customer_service', 'perm_dashboard_basic_view', 5202),
('role_admin', 'perm_dashboard_quality_view', 5301),
('role_customer_service', 'perm_dashboard_quality_view', 5302),
('role_admin', 'perm_demo_data_manage', 5401),
('role_customer_service', 'perm_demo_data_manage', 5402)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO role_permission (role_id, permission_id, sort) VALUES
('role_nurse', 'perm_nurse_appeal_create', 4600)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO reminder_task (
  task_id, elder_id, reminder_type, title, content, scheduled_at, reminder_status, source_type, source_id, created_by
) VALUES
('reminder_032_med', 'elder_001', 'MEDICATION', '早间用药提醒', '按医嘱记录降压药服用情况。', '2026-07-15 08:00:00', 'DONE', 'MEDICATION_PLAN', 'med_001', 'family-001'),
('reminder_032_rehab', 'elder_001', 'REHAB', '康复训练提醒', '完成十分钟下肢活动并注意防跌倒。', '2026-07-15 10:00:00', 'SNOOZED', 'CARE_PLAN', 'plan_001', 'family-001'),
('reminder_032_revisit', 'elder_001', 'REVISIT', '复诊提醒', '准备近期血压记录用于复诊沟通。', '2026-07-15 15:00:00', 'MISSED', 'MEDICAL_FILE', 'medical_file_001', 'family-001'),
('reminder_032_help', 'elder_001', 'MEASUREMENT', '血压测量提醒', '如测量不适可请求家属或客服协助。', '2026-07-15 20:00:00', 'NEED_HELP', 'HEALTH_ARCHIVE', 'archive_001', 'family-001'),
('reminder_051_follow', 'elder_001', 'FOLLOW_UP', '随访提醒', '客服随访后生成的下次跟进提醒。', '2026-07-28 09:00:00', 'PENDING', 'FOLLOW_UP', 'follow_051_001', 'cs-001')
ON DUPLICATE KEY UPDATE
  reminder_type = VALUES(reminder_type),
  title = VALUES(title),
  content = VALUES(content),
  scheduled_at = VALUES(scheduled_at),
  reminder_status = VALUES(reminder_status),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  created_by = VALUES(created_by);

INSERT INTO reminder_record (
  record_id, task_id, elder_id, result, remark, snooze_minutes, operator_id, operated_at
) VALUES
('reminder_record_done', 'reminder_032_med', 'elder_001', 'DONE', '已按医嘱记录。', NULL, 'elder-001', '2026-07-15 08:05:00'),
('reminder_record_snooze', 'reminder_032_rehab', 'elder_001', 'SNOOZED', '稍后训练。', 30, 'elder-001', '2026-07-15 10:02:00'),
('reminder_record_missed', 'reminder_032_revisit', 'elder_001', 'MISSED', '提醒过期未处理。', NULL, 'family-001', '2026-07-15 16:00:00'),
('reminder_record_help', 'reminder_032_help', 'elder_001', 'NEED_HELP', '测量时头晕，请求协助。', NULL, 'elder-001', '2026-07-15 20:01:00')
ON DUPLICATE KEY UPDATE
  result = VALUES(result),
  remark = VALUES(remark),
  snooze_minutes = VALUES(snooze_minutes),
  operator_id = VALUES(operator_id),
  operated_at = VALUES(operated_at);

INSERT INTO care_metric_config (
  config_id, service_id, config_version, config_status, created_by
) VALUES (
  'metric_cfg_034_001', 'service_001', 1, 'ACTIVE', 'admin-001'
) ON DUPLICATE KEY UPDATE
  config_version = VALUES(config_version),
  config_status = VALUES(config_status),
  created_by = VALUES(created_by);

INSERT INTO care_metric_item (
  metric_item_id, config_id, service_id, metric_code, metric_name, metric_type,
  required, evidence_type, expected_action, score_weight, description, sort, enabled
) VALUES
('metric_item_bp', 'metric_cfg_034_001', 'service_001', 'BLOOD_PRESSURE', '血压测量', 'SERVICE_PROCESS', 1, 'VITAL_SIGN', '服务中完成血压测量并记录。', 20.00, '基础上门护理必填指标。', 1, 1),
('metric_item_photo', 'metric_cfg_034_001', 'service_001', 'SERVICE_PHOTO', '服务照片', 'POST_SERVICE', 1, 'PHOTO', '上传服务后环境或护理完成照片。', 10.00, '用于留档完整性检查。', 2, 1),
('metric_item_summary', 'metric_cfg_034_001', 'service_001', 'SERVICE_SUMMARY', '服务总结', 'POST_SERVICE', 1, 'TEXT', '填写服务总结。', 15.00, '服务记录摘要。', 3, 1)
ON DUPLICATE KEY UPDATE
  metric_name = VALUES(metric_name),
  metric_type = VALUES(metric_type),
  required = VALUES(required),
  evidence_type = VALUES(evidence_type),
  expected_action = VALUES(expected_action),
  score_weight = VALUES(score_weight),
  description = VALUES(description),
  sort = VALUES(sort),
  enabled = VALUES(enabled);

INSERT INTO metric_score_rule (
  score_rule_id, metric_item_id, rule_type, score_delta, description, enabled
) VALUES
('score_rule_bp_pass', 'metric_item_bp', 'PASS', 0.50, '按要求完成血压测量。', 1),
('score_rule_photo_missing', 'metric_item_photo', 'MISSING', -2.00, '缺少必填照片留档。', 1),
('score_rule_photo_exempt', 'metric_item_photo', 'EXEMPT_APPROVED', 0.00, '客观原因豁免后不扣分。', 1),
('score_rule_complaint', NULL, 'COMPLAINT', -5.00, '有效投诉扣分。', 1),
('score_rule_appeal', NULL, 'APPEAL', 2.00, '申诉通过加回分数。', 1)
ON DUPLICATE KEY UPDATE
  metric_item_id = VALUES(metric_item_id),
  rule_type = VALUES(rule_type),
  score_delta = VALUES(score_delta),
  description = VALUES(description),
  enabled = VALUES(enabled);

INSERT INTO order_metric_checklist (
  checklist_id, order_id, service_id, config_id, config_version, generated_by
) VALUES (
  'checklist_035_001', 'order_031_001', 'service_001', 'metric_cfg_034_001', 1, 'admin-001'
) ON DUPLICATE KEY UPDATE
  service_id = VALUES(service_id),
  config_id = VALUES(config_id),
  config_version = VALUES(config_version),
  generated_by = VALUES(generated_by);

INSERT INTO order_metric_item (
  order_metric_item_id, checklist_id, order_id, metric_item_id, metric_code, metric_name,
  required, evidence_type, score_weight, metric_status, submitted_at, reviewed_at
) VALUES
('order_metric_bp', 'checklist_035_001', 'order_031_001', 'metric_item_bp', 'BLOOD_PRESSURE', '血压测量', 1, 'VITAL_SIGN', 20.00, 'PASS', '2026-07-24 09:30:00', '2026-07-24 10:00:00'),
('order_metric_photo', 'checklist_035_001', 'order_031_001', 'metric_item_photo', 'SERVICE_PHOTO', '服务照片', 1, 'PHOTO', 10.00, 'PENDING_PROOF', '2026-07-24 09:50:00', NULL),
('order_metric_summary', 'checklist_035_001', 'order_031_001', 'metric_item_summary', 'SERVICE_SUMMARY', '服务总结', 1, 'TEXT', 15.00, 'SUBMITTED', '2026-07-24 10:00:00', NULL)
ON DUPLICATE KEY UPDATE
  metric_name = VALUES(metric_name),
  required = VALUES(required),
  evidence_type = VALUES(evidence_type),
  score_weight = VALUES(score_weight),
  metric_status = VALUES(metric_status),
  submitted_at = VALUES(submitted_at),
  reviewed_at = VALUES(reviewed_at);

INSERT INTO file_asset (
  file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by
) VALUES
('file_evidence_036', 'service-evidence.jpg', 'image/jpeg', 1024, 'smart-nursing', 'demo/evidence/service-evidence.jpg', 'APPROVED', 'nurse-reco-a-029'),
('file_appeal_046', 'appeal-proof.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/appeal/appeal-proof.pdf', 'APPROVED', 'nurse-reco-a-029'),
('file_article_049', 'fall-prevention-training.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/training/fall-prevention-training.pdf', 'APPROVED', 'admin-001')
ON DUPLICATE KEY UPDATE
  original_name = VALUES(original_name),
  mime_type = VALUES(mime_type),
  file_size = VALUES(file_size),
  storage_bucket = VALUES(storage_bucket),
  object_key = VALUES(object_key),
  audit_status = VALUES(audit_status),
  uploaded_by = VALUES(uploaded_by);

INSERT INTO care_service_evidence (
  evidence_id, order_id, task_id, order_metric_item_id, nurse_id, file_id,
  evidence_type, description, audit_status, submitted_at, reviewed_by, reviewed_at, review_comment
) VALUES
('evidence_036_bp', 'order_031_001', 'task_031_001', 'order_metric_bp', 'nurse-reco-a-029', NULL, 'VITAL_SIGN', '血压 128/82，已记录。', 'APPROVED', '2026-07-24 09:30:00', 'admin-001', '2026-07-24 10:00:00', '生命体征记录完整。'),
('evidence_036_photo', 'order_031_001', 'task_031_001', 'order_metric_photo', 'nurse-reco-a-029', 'file_evidence_036', 'PHOTO', '老人拒绝拍摄正面，仅上传环境留档。', 'PENDING', '2026-07-24 09:50:00', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
  order_metric_item_id = VALUES(order_metric_item_id),
  file_id = VALUES(file_id),
  evidence_type = VALUES(evidence_type),
  description = VALUES(description),
  audit_status = VALUES(audit_status),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at),
  review_comment = VALUES(review_comment);

INSERT INTO evidence_review_record (
  review_record_id, evidence_id, from_status, to_status, review_comment, reviewer_id
) VALUES (
  'evidence_review_037', 'evidence_036_bp', 'PENDING', 'APPROVED', '血压记录完整。', 'admin-001'
) ON DUPLICATE KEY UPDATE
  from_status = VALUES(from_status),
  to_status = VALUES(to_status),
  review_comment = VALUES(review_comment),
  reviewer_id = VALUES(reviewer_id);

INSERT INTO metric_exception_proof (
  proof_id, order_metric_item_id, evidence_id, nurse_id, reason_type, reason,
  proof_status, review_comment, reviewed_by, reviewed_at
) VALUES (
  'proof_039_photo', 'order_metric_photo', 'evidence_036_photo', 'nurse-reco-a-029',
  'ELDER_REFUSED', '长辈拒绝拍摄本人照片，仅允许环境照片留档。', 'APPROVED',
  '情况属实，不扣分。', 'admin-001', '2026-07-24 10:20:00'
) ON DUPLICATE KEY UPDATE
  evidence_id = VALUES(evidence_id),
  reason_type = VALUES(reason_type),
  reason = VALUES(reason),
  proof_status = VALUES(proof_status),
  review_comment = VALUES(review_comment),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at);

INSERT INTO nurse_metric_record (
  metric_record_id, nurse_id, order_id, order_metric_item_id, metric_status, score_delta, source_type, source_id, recorded_at
) VALUES
('metric_record_bp', 'nurse-reco-a-029', 'order_031_001', 'order_metric_bp', 'PASS', 0.50, 'ORDER_METRIC_ITEM', 'order_metric_bp', '2026-07-24 10:00:00'),
('metric_record_photo', 'nurse-reco-a-029', 'order_031_001', 'order_metric_photo', 'EXEMPT_APPROVED', 0.00, 'METRIC_EXCEPTION_PROOF', 'proof_039_photo', '2026-07-24 10:20:00')
ON DUPLICATE KEY UPDATE
  metric_status = VALUES(metric_status),
  score_delta = VALUES(score_delta),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  recorded_at = VALUES(recorded_at);

INSERT INTO ai_assistant_session (
  session_id, elder_id, user_id, session_title, session_status, safety_level, risk_flag, trace_id, source_type
) VALUES (
  'ai_session_041', 'elder_001', 'elder-001', '血压与头晕咨询', 'ACTIVE', 'WARNING', 1, 'trace-ai-041', 'VOICE'
) ON DUPLICATE KEY UPDATE
  session_title = VALUES(session_title),
  session_status = VALUES(session_status),
  safety_level = VALUES(safety_level),
  risk_flag = VALUES(risk_flag),
  trace_id = VALUES(trace_id),
  source_type = VALUES(source_type);

INSERT INTO voice_command_log (
  voice_log_id, user_id, file_id, intent_type, source_biz_type, source_biz_id,
  session_id, trace_id, safety_flag, risk_level
) VALUES (
  'voice_ai_041', 'elder-001', NULL, 'HEALTH_CONSULT', 'AI_SESSION', 'ai_session_041',
  'ai_session_041', 'trace-ai-041', 1, 'WARNING'
) ON DUPLICATE KEY UPDATE
  intent_type = VALUES(intent_type),
  source_biz_type = VALUES(source_biz_type),
  source_biz_id = VALUES(source_biz_id),
  session_id = VALUES(session_id),
  trace_id = VALUES(trace_id),
  safety_flag = VALUES(safety_flag),
  risk_level = VALUES(risk_level);

INSERT INTO ai_assistant_message (
  message_id, session_id, sender_role, message_type, content_summary, content_text, voice_log_id, safety_flag, trace_id
) VALUES
('ai_msg_041_user', 'ai_session_041', 'USER', 'VOICE', '长辈反馈起身头晕并询问是否需要帮助。', '我起身有点头晕，需要联系家属吗？', 'voice_ai_041', 1, 'trace-ai-041'),
('ai_msg_041_assist', 'ai_session_041', 'ASSISTANT', 'TEXT', '建议先坐下休息并联系家属或客服，不提供诊断。', '请先坐下休息，如持续不适请联系家属或客服协助。', NULL, 1, 'trace-ai-041')
ON DUPLICATE KEY UPDATE
  content_summary = VALUES(content_summary),
  content_text = VALUES(content_text),
  voice_log_id = VALUES(voice_log_id),
  safety_flag = VALUES(safety_flag),
  trace_id = VALUES(trace_id);

INSERT INTO assistance_ticket (
  assistance_ticket_id, elder_id, requester_id, session_id, category, priority,
  ticket_status, description, source_type
) VALUES (
  'assist_043_001', 'elder_001', 'elder-001', 'ai_session_041', 'HEALTH_HELP', 'URGENT',
  'PROCESSING', 'AI 会话触发人工协助：长辈反馈起身头晕。', 'AI'
) ON DUPLICATE KEY UPDATE
  category = VALUES(category),
  priority = VALUES(priority),
  ticket_status = VALUES(ticket_status),
  description = VALUES(description),
  source_type = VALUES(source_type);

INSERT INTO customer_service_ticket (
  ticket_id, assistance_ticket_id, elder_id, requester_id, assigned_to, category, priority,
  ticket_status, description, source_type, source_id, resolved_at
) VALUES (
  'cs_ticket_043', 'assist_043_001', 'elder_001', 'elder-001', 'cs-001', 'HEALTH_HELP', 'URGENT',
  'RESOLVED', '长辈头晕协助工单，已联系家属确认。', 'AI', 'ai_session_041', '2026-07-15 20:30:00'
) ON DUPLICATE KEY UPDATE
  assigned_to = VALUES(assigned_to),
  priority = VALUES(priority),
  ticket_status = VALUES(ticket_status),
  description = VALUES(description),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  resolved_at = VALUES(resolved_at);

INSERT INTO ticket_message (
  message_id, ticket_id, sender_id, sender_role, message_type, content
) VALUES
('ticket_msg_043_001', 'cs_ticket_043', 'cs-001', 'CUSTOMER_SERVICE', 'PHONE_NOTE', '已电话联系家属，建议陪同测量血压并持续观察。'),
('ticket_msg_043_002', 'cs_ticket_043', 'family-001', 'FAMILY', 'TEXT', '已到家陪同，状态稳定。')
ON DUPLICATE KEY UPDATE
  sender_id = VALUES(sender_id),
  sender_role = VALUES(sender_role),
  message_type = VALUES(message_type),
  content = VALUES(content);

INSERT INTO review (
  review_id, order_id, report_id, reviewer_id, reviewer_role, rating, satisfaction, content
) VALUES (
  'review_045_001', 'order_001', 'report_001', 'family-001', 'FAMILY', 4, 4, '服务完成，报告清晰，但希望后续提醒更及时。'
) ON DUPLICATE KEY UPDATE
  rating = VALUES(rating),
  satisfaction = VALUES(satisfaction),
  content = VALUES(content);

INSERT INTO complaint (
  complaint_id, review_id, order_id, complainant_id, complaint_status, content,
  handled_by, handle_result, handled_at
) VALUES (
  'complaint_045', 'review_045_001', 'order_001', 'family-001', 'RESOLVED',
  '家属反馈服务照片不完整。', 'cs-001', '已核实为长辈拒绝拍摄，记录为豁免。', '2026-07-24 10:30:00'
) ON DUPLICATE KEY UPDATE
  complaint_status = VALUES(complaint_status),
  content = VALUES(content),
  handled_by = VALUES(handled_by),
  handle_result = VALUES(handle_result),
  handled_at = VALUES(handled_at);

INSERT INTO nurse_appeal (
  appeal_id, nurse_id, target_type, target_id, reason, file_ids, appeal_status,
  score_adjustment, review_comment, reviewed_by, reviewed_at
) VALUES (
  'appeal_046_001', 'nurse-reco-a-029', 'METRIC_EXCEPTION', 'proof_039_photo',
  '照片缺失因长辈拒绝拍摄，已上传环境照片作为佐证。', JSON_ARRAY('file_appeal_046'),
  'APPROVED', 2.00, '申诉成立，恢复相应评分。', 'admin-001', '2026-07-24 10:40:00'
) ON DUPLICATE KEY UPDATE
  reason = VALUES(reason),
  file_ids = VALUES(file_ids),
  appeal_status = VALUES(appeal_status),
  score_adjustment = VALUES(score_adjustment),
  review_comment = VALUES(review_comment),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at);

INSERT INTO nurse_score (
  nurse_id, total_score, service_count, positive_rate, complaint_count,
  last_service_at, updated_by
) VALUES (
  'nurse-001', 88.00, 6, 96.00, 1, '2026-07-20 10:00:00', 'admin-001'
) ON DUPLICATE KEY UPDATE
  total_score = VALUES(total_score),
  service_count = VALUES(service_count),
  positive_rate = VALUES(positive_rate),
  complaint_count = VALUES(complaint_count),
  last_service_at = VALUES(last_service_at),
  updated_by = VALUES(updated_by);

INSERT INTO nurse_score_change_log (
  change_log_id, nurse_id, source_event_type, source_event_id,
  before_score, after_score, score_delta, reason, changed_by
) VALUES
('score_log_047_nurse_demo', 'nurse-001', 'COMPLAINT', 'complaint_045', 92.00, 88.00, -4.00, '收到服务资料完整性投诉，暂扣护理评分，护理人员可提交申诉复核。', 'admin-001'),
('score_log_047_metric', 'nurse-reco-a-029', 'METRIC', 'metric_record_bp', 96.50, 97.00, 0.50, '按要求完成血压测量。', 'admin-001'),
('score_log_047_appeal', 'nurse-reco-a-029', 'APPEAL', 'appeal_046_001', 95.00, 97.00, 2.00, '申诉通过，恢复照片缺失扣分。', 'admin-001')
ON DUPLICATE KEY UPDATE
  source_event_type = VALUES(source_event_type),
  source_event_id = VALUES(source_event_id),
  before_score = VALUES(before_score),
  after_score = VALUES(after_score),
  score_delta = VALUES(score_delta),
  reason = VALUES(reason),
  changed_by = VALUES(changed_by);

INSERT INTO training_article (
  article_id, title, content_summary, content_url, file_id, service_id,
  required_reading, article_status, created_by, published_at
) VALUES (
  'article_049_001', '跌倒风险护理要点', '面向基础上门护理的防跌倒观察、搀扶和记录要求。', '/training/fall-prevention.html',
  'file_article_049', 'service_001', 1, 'PUBLISHED', 'admin-001', '2026-07-15 11:00:00'
) ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content_summary = VALUES(content_summary),
  content_url = VALUES(content_url),
  file_id = VALUES(file_id),
  service_id = VALUES(service_id),
  required_reading = VALUES(required_reading),
  article_status = VALUES(article_status),
  created_by = VALUES(created_by),
  published_at = VALUES(published_at);

INSERT INTO article_tag (
  tag_id, article_id, tag_code, tag_name
) VALUES
('article_tag_fall', 'article_049_001', 'FALL_RISK', '跌倒风险'),
('article_tag_basic', 'article_049_001', 'BASIC_CARE', '基础照护')
ON DUPLICATE KEY UPDATE
  tag_code = VALUES(tag_code),
  tag_name = VALUES(tag_name);

INSERT INTO article_recommend_rule (
  rule_id, article_id, service_id, care_level, skill_code, enabled, sort
) VALUES (
  'article_rule_049', 'article_049_001', 'service_001', 'LEVEL_2', 'BASIC_CARE', 1, 1
) ON DUPLICATE KEY UPDATE
  service_id = VALUES(service_id),
  care_level = VALUES(care_level),
  skill_code = VALUES(skill_code),
  enabled = VALUES(enabled),
  sort = VALUES(sort);

INSERT INTO nurse_article_reading (
  reading_id, article_id, nurse_id, reading_status, read_at, confirmed_at
) VALUES (
  'reading_050_001', 'article_049_001', 'nurse-reco-a-029', 'CONFIRMED',
  '2026-07-15 11:20:00', '2026-07-15 11:25:00'
) ON DUPLICATE KEY UPDATE
  reading_status = VALUES(reading_status),
  read_at = VALUES(read_at),
  confirmed_at = VALUES(confirmed_at);

INSERT INTO follow_up_record (
  follow_up_id, elder_id, order_id, ticket_id, follow_up_type, content,
  next_follow_up_at, need_reminder, created_reminder_task_id, created_by
) VALUES (
  'follow_051_001', 'elder_001', 'order_001', 'cs_ticket_043', 'CUSTOMER_SERVICE',
  '客服回访确认头晕情况已缓解，建议一周后再次跟进。', '2026-07-28 09:00:00',
  1, 'reminder_051_follow', 'cs-001'
) ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  ticket_id = VALUES(ticket_id),
  follow_up_type = VALUES(follow_up_type),
  content = VALUES(content),
  next_follow_up_at = VALUES(next_follow_up_at),
  need_reminder = VALUES(need_reminder),
  created_reminder_task_id = VALUES(created_reminder_task_id),
  created_by = VALUES(created_by);

INSERT INTO bug_list (
  bug_id, related_stage, bug_title, severity, bug_status, owner, workaround, closed_at
) VALUES
('bug_055_closed', '52', '基础看板统计口径已补充真实数据源说明', 'LOW', 'CLOSED', 'member1', '已在阶段32-55字典和验收记录中说明统计来源。', '2026-07-15 16:00:00'),
('bug_055_deferred', '55', '完整四端真实联调需全员合并后执行', 'MEDIUM', 'DEFERRED', 'team', '数据库数据已准备，后端和前端合并后再执行端到端验收。', NULL)
ON DUPLICATE KEY UPDATE
  related_stage = VALUES(related_stage),
  bug_title = VALUES(bug_title),
  severity = VALUES(severity),
  bug_status = VALUES(bug_status),
  owner = VALUES(owner),
  workaround = VALUES(workaround),
  closed_at = VALUES(closed_at);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id
) VALUES (
  'op_seed_032_055', 'admin-001', 'ADMIN', 'SEED_PHASE_32_55', 'DEMO_DATA', 'phase-32-55',
  JSON_OBJECT(
    'reminderRecords', 4,
    'metricItems', 3,
    'evidenceRecords', 2,
    'aiSessions', 1,
    'customerServiceTickets', 1,
    'scoreLogs', 2,
    'dashboardSourcesReady', true
  ),
  'seed-phase-32-55'
) ON DUPLICATE KEY UPDATE
  after_value = VALUES(after_value),
  trace_id = VALUES(trace_id);
