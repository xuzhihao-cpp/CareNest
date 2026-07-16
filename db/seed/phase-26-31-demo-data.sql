USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO sys_permission (permission_id, permission_code, permission_name, permission_group, enabled) VALUES
('perm_nurse_qual_submit', 'NURSE_QUALIFICATION_SUBMIT', '提交护理资质', 'nurse_admission', 1),
('perm_nurse_qual_review', 'NURSE_QUALIFICATION_REVIEW', '审核护理资质', 'nurse_admission', 1),
('perm_nurse_training_review', 'NURSE_TRAINING_REVIEW', '审核培训资格', 'nurse_admission', 1),
('perm_nurse_recommend_view', 'NURSE_RECOMMEND_VIEW', '查看护理推荐', 'nurse_recommendation', 1),
('perm_nurse_preference_select', 'NURSE_PREFERENCE_SELECT', '选择偏好护理', 'nurse_recommendation', 1),
('perm_nurse_attention_ack', 'NURSE_ATTENTION_ACK', '确认服务前注意事项', 'care_attention', 1),
('perm_care_attention_review', 'CARE_ATTENTION_REVIEW', '审阅服务前注意事项', 'care_attention', 1)
ON DUPLICATE KEY UPDATE
  permission_code = VALUES(permission_code),
  permission_name = VALUES(permission_name),
  permission_group = VALUES(permission_group),
  enabled = VALUES(enabled);

INSERT INTO role_permission (role_id, permission_id, sort) VALUES
('role_nurse', 'perm_nurse_qual_submit', 2601),
('role_admin', 'perm_nurse_qual_review', 2701),
('role_customer_service', 'perm_nurse_qual_review', 2702),
('role_admin', 'perm_nurse_training_review', 2801),
('role_customer_service', 'perm_nurse_training_review', 2802),
('role_family', 'perm_nurse_recommend_view', 2901),
('role_admin', 'perm_nurse_recommend_view', 2902),
('role_nurse', 'perm_nurse_recommend_view', 2903),
('role_family', 'perm_nurse_preference_select', 3001),
('role_nurse', 'perm_nurse_attention_ack', 3101),
('role_admin', 'perm_care_attention_review', 3102),
('role_customer_service', 'perm_care_attention_review', 3103)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO nurse_skill_dictionary (skill_code, skill_name, sort, enabled) VALUES
('BASIC_CARE', '基础照护', 10, 1),
('VITAL_SIGN', '生命体征观察', 20, 1),
('REHAB_ASSIST', '康复陪护', 30, 1)
ON DUPLICATE KEY UPDATE
  skill_name = VALUES(skill_name),
  sort = VALUES(sort),
  enabled = VALUES(enabled);

INSERT INTO sys_user (user_id, username, password_hash, display_name, phone, account_status) VALUES
('nurse-noapp-026', 'nurse_noapp_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '无申请护理演示', '13800002601', 'ENABLED'),
('nurse-pending-026', 'nurse_pending_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '待审核护理演示', '13800002602', 'ENABLED'),
('nurse-needmore-026', 'nurse_needmore_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '需补充护理演示', '13800002603', 'ENABLED'),
('nurse-qualified-026', 'nurse_qualified_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '资质通过护理演示', '13800002604', 'ENABLED'),
('nurse-valid-028', 'nurse_training_valid_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '培训有效护理演示', '13800002801', 'ENABLED'),
('nurse-expired-028', 'nurse_training_expired_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '培训过期护理演示', '13800002802', 'ENABLED'),
('nurse-reco-a-029', 'nurse_reco_a_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '推荐护理甲', '13800002901', 'ENABLED'),
('nurse-reco-b-029', 'nurse_reco_b_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '推荐护理乙', '13800002902', 'ENABLED')
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  phone = VALUES(phone),
  account_status = VALUES(account_status);

INSERT INTO user_role (user_id, role_id)
SELECT user_id, 'role_nurse'
FROM sys_user
WHERE user_id IN (
  'nurse-noapp-026', 'nurse-pending-026', 'nurse-needmore-026', 'nurse-qualified-026',
  'nurse-valid-028', 'nurse-expired-028', 'nurse-reco-a-029', 'nurse-reco-b-029'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO nurse_profile (
  nurse_id, real_name, id_no_masked, qualification_status, training_status, can_take_order, profile_status
) VALUES
('nurse-001', '护理演示账号', '310***********0003', 'PENDING', 'PENDING', 0, 'ACTIVE'),
('nurse-noapp-026', '无申请护理', '310***********2601', 'PENDING', 'PENDING', 0, 'ACTIVE'),
('nurse-pending-026', '待审核护理', '310***********2602', 'PENDING', 'PENDING', 0, 'ACTIVE'),
('nurse-needmore-026', '需补充护理', '310***********2603', 'NEED_MORE', 'PENDING', 0, 'ACTIVE'),
('nurse-qualified-026', '资质通过护理', '310***********2604', 'APPROVED', 'REJECTED', 0, 'ACTIVE'),
('nurse-valid-028', '培训有效护理', '310***********2801', 'APPROVED', 'APPROVED', 1, 'ACTIVE'),
('nurse-expired-028', '培训过期护理', '310***********2802', 'APPROVED', 'APPROVED', 0, 'ACTIVE'),
('nurse-reco-a-029', '推荐护理甲', '310***********2901', 'APPROVED', 'APPROVED', 1, 'ACTIVE'),
('nurse-reco-b-029', '推荐护理乙', '310***********2902', 'APPROVED', 'APPROVED', 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  id_no_masked = VALUES(id_no_masked),
  qualification_status = VALUES(qualification_status),
  training_status = VALUES(training_status),
  can_take_order = VALUES(can_take_order),
  profile_status = VALUES(profile_status);

INSERT INTO file_asset (
  file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by
) VALUES
('file_qual_pending_026', 'nurse-pending-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/pending/certificate.pdf', 'APPROVED', 'nurse-pending-026'),
('file_qual_needmore_026', 'nurse-needmore-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/needmore/certificate.pdf', 'APPROVED', 'nurse-needmore-026'),
('file_qual_approved_026', 'nurse-qualified-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/qualified/certificate.pdf', 'APPROVED', 'nurse-qualified-026'),
('file_qual_valid_028', 'nurse-valid-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/valid/certificate.pdf', 'APPROVED', 'nurse-valid-028'),
('file_qual_expired_028', 'nurse-expired-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/expired/certificate.pdf', 'APPROVED', 'nurse-expired-028'),
('file_qual_reco_a_029', 'nurse-reco-a-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/reco-a/certificate.pdf', 'APPROVED', 'nurse-reco-a-029'),
('file_qual_reco_b_029', 'nurse-reco-b-certificate.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/nurse/reco-b/certificate.pdf', 'APPROVED', 'nurse-reco-b-029')
ON DUPLICATE KEY UPDATE
  original_name = VALUES(original_name),
  mime_type = VALUES(mime_type),
  file_size = VALUES(file_size),
  storage_bucket = VALUES(storage_bucket),
  object_key = VALUES(object_key),
  audit_status = VALUES(audit_status),
  uploaded_by = VALUES(uploaded_by);

INSERT INTO nurse_certificate (
  certificate_id, application_id, nurse_id, certificate_no, file_id,
  service_skill_codes, audit_status, review_comment, reviewed_by, reviewed_at
) VALUES
('cert_pending_026', 'app_pending_026', 'nurse-pending-026', 'CERT-PENDING-026', 'file_qual_pending_026', JSON_ARRAY('BASIC_CARE'), 'PENDING', NULL, NULL, NULL),
('cert_needmore_026', 'app_needmore_026', 'nurse-needmore-026', 'CERT-NEEDMORE-026', 'file_qual_needmore_026', JSON_ARRAY('BASIC_CARE'), 'NEED_MORE', '证书照片边缘不清晰，请补充原件扫描件。', 'admin-001', '2026-07-15 09:10:00'),
('cert_approved_026', 'app_approved_026', 'nurse-qualified-026', 'CERT-APPROVED-026', 'file_qual_approved_026', JSON_ARRAY('BASIC_CARE'), 'APPROVED', '资质材料审核通过。', 'admin-001', '2026-07-15 09:20:00'),
('cert_valid_028', 'app_valid_028', 'nurse-valid-028', 'CERT-VALID-028', 'file_qual_valid_028', JSON_ARRAY('BASIC_CARE','VITAL_SIGN'), 'APPROVED', '资质材料审核通过。', 'admin-001', '2026-07-15 09:30:00'),
('cert_expired_028', 'app_expired_028', 'nurse-expired-028', 'CERT-EXPIRED-028', 'file_qual_expired_028', JSON_ARRAY('BASIC_CARE'), 'APPROVED', '资质材料审核通过。', 'admin-001', '2026-07-15 09:40:00'),
('cert_reco_a_029', 'app_reco_a_029', 'nurse-reco-a-029', 'CERT-RECO-A-029', 'file_qual_reco_a_029', JSON_ARRAY('BASIC_CARE','VITAL_SIGN'), 'APPROVED', '资质材料审核通过。', 'admin-001', '2026-07-15 09:50:00'),
('cert_reco_b_029', 'app_reco_b_029', 'nurse-reco-b-029', 'CERT-RECO-B-029', 'file_qual_reco_b_029', JSON_ARRAY('BASIC_CARE','REHAB_ASSIST'), 'APPROVED', '资质材料审核通过。', 'admin-001', '2026-07-15 10:00:00')
ON DUPLICATE KEY UPDATE
  certificate_no = VALUES(certificate_no),
  file_id = VALUES(file_id),
  service_skill_codes = VALUES(service_skill_codes),
  audit_status = VALUES(audit_status),
  review_comment = VALUES(review_comment),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at);

INSERT INTO nurse_training_record (
  training_id, nurse_id, training_status, training_batch, passed_at, expired_at, remark, reviewed_by, reviewed_at
) VALUES
('training_rejected_026', 'nurse-qualified-026', 'REJECTED', 'TRAIN-2026-07-A', NULL, NULL, '实操考核未通过，需重新培训。', 'admin-001', '2026-07-15 10:10:00'),
('training_valid_028', 'nurse-valid-028', 'APPROVED', 'TRAIN-2026-07-A', '2026-07-15 10:20:00', '2030-12-31 23:59:59', '培训通过。', 'admin-001', '2026-07-15 10:20:00'),
('training_expired_028', 'nurse-expired-028', 'APPROVED', 'TRAIN-2025-01-A', '2025-01-15 10:20:00', '2025-06-30 23:59:59', '历史培训已过期，仅用于过期过滤验收。', 'admin-001', '2025-01-15 10:20:00'),
('training_reco_a_029', 'nurse-reco-a-029', 'APPROVED', 'TRAIN-2026-07-A', '2026-07-15 10:30:00', '2030-12-31 23:59:59', '培训通过。', 'admin-001', '2026-07-15 10:30:00'),
('training_reco_b_029', 'nurse-reco-b-029', 'APPROVED', 'TRAIN-2026-07-A', '2026-07-15 10:40:00', '2030-12-31 23:59:59', '培训通过。', 'admin-001', '2026-07-15 10:40:00')
ON DUPLICATE KEY UPDATE
  training_status = VALUES(training_status),
  training_batch = VALUES(training_batch),
  passed_at = VALUES(passed_at),
  expired_at = VALUES(expired_at),
  remark = VALUES(remark),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at);

INSERT INTO service_item (
  service_id, service_name, service_desc, price_cent, duration_minutes, service_status, sort
) VALUES (
  'service_029_none', '无候选专项护理', '阶段29无候选推荐场景，不配置任何护理技能。', 39900, 90, 'ON_SHELF', 29
) ON DUPLICATE KEY UPDATE
  service_name = VALUES(service_name),
  service_desc = VALUES(service_desc),
  price_cent = VALUES(price_cent),
  duration_minutes = VALUES(duration_minutes),
  service_status = VALUES(service_status),
  sort = VALUES(sort);

INSERT INTO nurse_service_skill (
  skill_id, nurse_id, service_id, skill_code, skill_name, enabled
) VALUES
('skill_valid_028_basic', 'nurse-valid-028', 'service_001', 'BASIC_CARE', '基础照护', 1),
('skill_reco_a_basic', 'nurse-reco-a-029', 'service_001', 'BASIC_CARE', '基础照护', 1),
('skill_reco_a_vital', 'nurse-reco-a-029', 'service_001', 'VITAL_SIGN', '生命体征观察', 1),
('skill_reco_b_basic', 'nurse-reco-b-029', 'service_001', 'BASIC_CARE', '基础照护', 1),
('skill_reco_b_rehab', 'nurse-reco-b-029', 'service_001', 'REHAB_ASSIST', '康复陪护', 1)
ON DUPLICATE KEY UPDATE
  service_id = VALUES(service_id),
  skill_code = VALUES(skill_code),
  skill_name = VALUES(skill_name),
  enabled = VALUES(enabled);

INSERT INTO nurse_score (
  nurse_id, total_score, service_count, positive_rate, complaint_count, last_service_at, updated_by
) VALUES
('nurse-valid-028', 82.00, 8, 95.00, 0, '2026-07-12 10:00:00', 'admin-001'),
('nurse-expired-028', 90.00, 12, 98.00, 0, '2026-06-01 10:00:00', 'admin-001'),
('nurse-reco-a-029', 96.50, 28, 99.00, 0, '2026-07-14 15:00:00', 'admin-001'),
('nurse-reco-b-029', 88.00, 16, 96.00, 1, '2026-07-13 16:00:00', 'admin-001')
ON DUPLICATE KEY UPDATE
  total_score = VALUES(total_score),
  service_count = VALUES(service_count),
  positive_rate = VALUES(positive_rate),
  complaint_count = VALUES(complaint_count),
  last_service_at = VALUES(last_service_at),
  updated_by = VALUES(updated_by);

INSERT INTO nursing_order (
  order_id, elder_id, family_id, service_id, address_id, service_address_snapshot, order_status,
  scheduled_start_at, scheduled_end_at, service_price_cent,
  contact_name, contact_phone, remark, created_by
) VALUES
('order_029_001', 'elder_001', 'family-001', 'service_001', 'address_001', '310101 人民路100号1单元201', 'WAIT_DISPATCH',
 '2026-07-22 09:00:00', '2026-07-22 10:00:00', 19900, '张小明', '13800000002', '阶段29/30推荐与偏好演示订单', 'family-001'),
('order_029_no_candidate', 'elder_001', 'family-001', 'service_029_none', 'address_001', '310101 人民路100号1单元201', 'WAIT_DISPATCH',
 '2026-07-23 09:00:00', '2026-07-23 10:30:00', 39900, '张小明', '13800000002', '阶段29无候选推荐演示订单', 'family-001'),
('order_031_001', 'elder_001', 'family-001', 'service_001', 'address_001', '310101 人民路100号1单元201', 'ACCEPTED',
 '2026-07-24 09:00:00', '2026-07-24 10:00:00', 19900, '张小明', '13800000002', '阶段31服务前注意事项演示订单', 'family-001')
ON DUPLICATE KEY UPDATE
  elder_id = VALUES(elder_id),
  family_id = VALUES(family_id),
  service_id = VALUES(service_id),
  address_id = VALUES(address_id),
  service_address_snapshot = VALUES(service_address_snapshot),
  order_status = VALUES(order_status),
  scheduled_start_at = VALUES(scheduled_start_at),
  scheduled_end_at = VALUES(scheduled_end_at),
  service_price_cent = VALUES(service_price_cent),
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  remark = VALUES(remark),
  created_by = VALUES(created_by),
  preferred_nurse_id = NULL,
  preferred_nurse_reason = NULL,
  preferred_recommendation_log_id = NULL,
  preferred_selected_at = NULL,
  preferred_selected_by = NULL;

INSERT INTO nurse_recommendation_log (
  recommendation_log_id, request_key, request_hash, order_id, elder_id, service_id, address_id,
  scheduled_start_at, nurse_id, score, matched_skills, recommend_reason, available, rank_no,
  candidate_snapshot, created_by
) VALUES
('reclog_029_001_a', 'recommend_029_001', SHA2('elder_001|service_001|address_001|2026-07-22T09:00:00|a', 256), 'order_029_001', 'elder_001', 'service_001', 'address_001',
 '2026-07-22 09:00:00', 'nurse-reco-a-029', 96.50, 'BASIC_CARE,VITAL_SIGN', '资质和培训有效，匹配基础照护、生命体征观察，综合评分更高。', 1, 1,
 JSON_OBJECT('qualificationStatus','APPROVED','trainingValid',true,'serviceCount',28,'positiveRate',99.00), 'family-001'),
('reclog_029_001_b', 'recommend_029_001', SHA2('elder_001|service_001|address_001|2026-07-22T09:00:00|b', 256), 'order_029_001', 'elder_001', 'service_001', 'address_001',
 '2026-07-22 09:00:00', 'nurse-reco-b-029', 88.00, 'BASIC_CARE,REHAB_ASSIST', '资质和培训有效，匹配基础照护、康复陪护，综合评分稳定。', 1, 2,
 JSON_OBJECT('qualificationStatus','APPROVED','trainingValid',true,'serviceCount',16,'positiveRate',96.00), 'family-001')
ON DUPLICATE KEY UPDATE
  request_key = VALUES(request_key),
  request_hash = VALUES(request_hash),
  order_id = VALUES(order_id),
  elder_id = VALUES(elder_id),
  service_id = VALUES(service_id),
  address_id = VALUES(address_id),
  scheduled_start_at = VALUES(scheduled_start_at),
  nurse_id = VALUES(nurse_id),
  score = VALUES(score),
  matched_skills = VALUES(matched_skills),
  recommend_reason = VALUES(recommend_reason),
  available = VALUES(available),
  rank_no = VALUES(rank_no),
  candidate_snapshot = VALUES(candidate_snapshot),
  created_by = VALUES(created_by);

UPDATE nursing_order
SET preferred_nurse_id = 'nurse-reco-a-029',
    preferred_nurse_reason = '资质和培训有效，匹配基础照护、生命体征观察，综合评分更高。',
    preferred_recommendation_log_id = 'reclog_029_001_a',
    preferred_selected_at = '2026-07-22 08:30:00',
    preferred_selected_by = 'family-001'
WHERE order_id = 'order_029_001';

UPDATE nurse_recommendation_log
SET selected_at = '2026-07-22 08:30:00',
    selected_by = 'family-001'
WHERE recommendation_log_id = 'reclog_029_001_a';

INSERT INTO nurse_task (
  task_id, order_id, nurse_id, task_status, dispatch_remark, accepted_at
) VALUES (
  'task_031_001', 'order_031_001', 'nurse-reco-a-029', 'ACCEPTED',
  '阶段31服务前注意事项演示任务', '2026-07-24 08:30:00'
) ON DUPLICATE KEY UPDATE
  nurse_id = VALUES(nurse_id),
  task_status = VALUES(task_status),
  dispatch_remark = VALUES(dispatch_remark),
  accepted_at = VALUES(accepted_at);

INSERT INTO care_attention_notice (
  notice_id, order_id, task_id, nurse_id, notice_level, content,
  source_type, source_id, source_version, required_ack, notice_hash, generated_by
) VALUES
('notice_031_critical', 'order_031_001', 'task_031_001', 'nurse-reco-a-029', 'CRITICAL',
 '服务前必须核对青霉素过敏史，护理过程中不得自行建议用药调整。', 'HEALTH_ARCHIVE', 'allergy_001', 'archive_001:v1', 1,
 SHA2('order_031_001|HEALTH_ARCHIVE|allergy_001|critical', 256), 'admin-001'),
('notice_031_warning', 'order_031_001', 'task_031_001', 'nurse-reco-a-029', 'WARNING',
 '长辈存在跌倒风险，上下楼和洗浴时需重点搀扶观察。', 'HEALTH_ARCHIVE', 'risk_001', 'archive_001:v1', 1,
 SHA2('order_031_001|HEALTH_ARCHIVE|risk_001|warning', 256), 'admin-001'),
('notice_031_info', 'order_031_001', 'task_031_001', 'nurse-reco-a-029', 'INFO',
 '本单服务项目包含生命体征观察，服务开始前准备血压测量记录。', 'SERVICE_ITEM', 'service_001', 'service_001:v1', 0,
 SHA2('order_031_001|SERVICE_ITEM|service_001|info', 256), 'admin-001')
ON DUPLICATE KEY UPDATE
  task_id = VALUES(task_id),
  nurse_id = VALUES(nurse_id),
  notice_level = VALUES(notice_level),
  content = VALUES(content),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  source_version = VALUES(source_version),
  required_ack = VALUES(required_ack),
  notice_hash = VALUES(notice_hash),
  generated_by = VALUES(generated_by),
  notice_status = 'ACTIVE';

INSERT INTO care_attention_ack (
  ack_id, notice_id, order_id, task_id, nurse_id, acked_by, acked_at
) VALUES (
  'ack_031_info', 'notice_031_info', 'order_031_001', 'task_031_001',
  'nurse-reco-a-029', 'nurse-reco-a-029', '2026-07-24 08:40:00'
) ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  task_id = VALUES(task_id),
  nurse_id = VALUES(nurse_id),
  acked_by = VALUES(acked_by),
  acked_at = VALUES(acked_at);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id
) VALUES (
  'op_seed_026_031', 'admin-001', 'ADMIN', 'SEED_PHASE_26_31', 'DEMO_DATA', 'phase-26-31',
  JSON_OBJECT(
    'qualificationScenarios', 6,
    'recommendableNurses', 2,
    'noCandidateServiceId', 'service_029_none',
    'attentionNotices', 3
  ),
  'seed-phase-26-31'
) ON DUPLICATE KEY UPDATE
  after_value = VALUES(after_value),
  trace_id = VALUES(trace_id);
