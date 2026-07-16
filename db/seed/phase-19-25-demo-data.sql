USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO sys_permission(permission_id,permission_code,permission_name,permission_group,enabled)
VALUES ('perm_health_archive_review','HEALTH_ARCHIVE_REVIEW','健康档案审核','health',1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),enabled=1;
INSERT IGNORE INTO role_permission(role_id,permission_id,sort)
SELECT role_id,'perm_health_archive_review',90 FROM sys_role WHERE role_code IN ('ADMIN','CUSTOMER_SERVICE');

INSERT INTO health_archive
  (archive_id, elder_id, archive_version, care_summary, updated_by)
VALUES
  ('archive_001', 'elder_001', 1, '血压需持续观察，服务前重点确认近期用药、过敏和跌倒风险。', 'family-001')
ON DUPLICATE KEY UPDATE
  care_summary = VALUES(care_summary),
  updated_by = VALUES(updated_by);

DELETE FROM chronic_disease WHERE elder_id = 'elder_001';
INSERT INTO chronic_disease
  (disease_id, elder_id, disease_name, disease_status, diagnosed_at, remark)
VALUES
  ('disease_001', 'elder_001', '高血压', 'ACTIVE', '2022-03-01', '按医嘱服药，定期测量血压');

DELETE FROM medication_plan WHERE elder_id = 'elder_001';
INSERT INTO medication_plan
  (medication_id, elder_id, medication_name, dosage, frequency, time_points, start_date, medication_status, remark)
VALUES
  ('med_001', 'elder_001', '降压药', '1片', 'ONCE_DAILY', JSON_ARRAY('08:00'), '2024-01-01', 'ACTIVE', '仅作记录，具体用药以医生医嘱为准');

DELETE FROM allergy_record WHERE elder_id = 'elder_001';
INSERT INTO allergy_record
  (allergy_id, elder_id, allergen, reaction, severity, remark)
VALUES
  ('allergy_001', 'elder_001', '青霉素', '皮疹', 'SEVERE', '护理前需主动核对');

DELETE FROM risk_tag WHERE elder_id = 'elder_001';
INSERT INTO risk_tag
  (risk_tag_id, elder_id, tag_code, tag_name, risk_level, remark)
VALUES
  ('risk_001', 'elder_001', 'FALL_RISK', '跌倒风险', 'MEDIUM', '上下楼和洗浴时需搀扶');

DELETE FROM care_plan WHERE elder_id = 'elder_001';
INSERT INTO care_plan
  (care_plan_id, elder_id, plan_content, plan_status)
VALUES
  ('plan_001', 'elder_001', JSON_OBJECT(
    'careGoals', '保持血压稳定并降低跌倒风险',
    'dailyCare', '每日记录血压和服药情况',
    'precautions', '服务前确认过敏史，护理过程中避免长时间站立'
  ), 'ACTIVE');

DELETE FROM medical_file WHERE medical_file_id IN ('medical_file_001', 'medical_file_002');
DELETE FROM file_asset WHERE file_id IN ('file_001', 'file_002');
INSERT INTO file_asset
  (file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by)
VALUES
  ('file_001', 'blood-pressure-report.pdf', 'application/pdf', 668, 'smart-nursing', 'demo/elder_001/blood-pressure-report.pdf', 'APPROVED', 'family-001'),
  ('file_002', 'lab-result.pdf', 'application/pdf', 660, 'smart-nursing', 'demo/elder_001/lab-result.pdf', 'PENDING', 'family-001');

INSERT INTO medical_file
  (medical_file_id, elder_id, file_id, file_type, title, occurred_at, audit_status, review_comment, uploader_id, reviewer_id, reviewed_at)
VALUES
  ('medical_file_001', 'elder_001', 'file_001', 'EXAMINATION_REPORT', '近期血压检查报告', '2026-07-01', 'APPROVED', '资料清晰，可用于服务前摘要。', 'family-001', 'admin-001', CURRENT_TIMESTAMP),
  ('medical_file_002', 'elder_001', 'file_002', 'EXAMINATION_REPORT', '待审核化验单', '2026-07-05', 'PENDING', NULL, 'family-001', NULL, NULL);

DELETE FROM elder_health_feedback WHERE feedback_id IN ('feedback_001');
INSERT INTO elder_health_feedback
  (feedback_id, elder_id, feedback_type, severity, content, input_type, created_by)
VALUES
  ('feedback_001', 'elder_001', 'DIZZINESS', 'MEDIUM', '今天起身时有些头晕，已坐下休息。', 'TEXT', 'elder-001');

DELETE FROM health_update_suggestion WHERE suggestion_id = 'suggestion_001';
DELETE FROM health_info_review_task WHERE review_task_id = 'review_task_019_001';
-- The suggestion and its review task reference each other during seed creation.
-- Both rows are inserted in this transaction before foreign-key checks resume.
SET FOREIGN_KEY_CHECKS = 0;
INSERT INTO health_update_suggestion
  (suggestion_id, elder_id, order_id, field_name, old_value, new_value, source_type, source_id, reason, suggestion_status, created_by, review_task_id)
VALUES
  ('suggestion_001', 'elder_001', 'order_001', 'riskTags', JSON_ARRAY(JSON_OBJECT('tagCode','FALL_RISK','tagName','跌倒风险')), JSON_OBJECT('tagCode','NIGHT_FALL_RISK','tagName','夜间跌倒风险'), 'SERVICE_RECORD', 'service_record_001', '护理记录提示夜间起身不稳，需要管理员审核后归档。', 'PENDING', 'nurse-001', 'review_task_019_001');

INSERT INTO health_info_review_task
  (review_task_id, suggestion_id, task_type, order_id, elder_id, field_name, old_value, new_value, source_type, source_id, review_status, created_by)
VALUES
  ('review_task_019_001', 'suggestion_001', 'HEALTH_UPDATE', 'order_001', 'elder_001', 'riskTags', JSON_ARRAY(JSON_OBJECT('tagCode','FALL_RISK','tagName','跌倒风险')), JSON_OBJECT('tagCode','NIGHT_FALL_RISK','tagName','夜间跌倒风险'), 'SERVICE_RECORD', 'service_record_001', 'PENDING', 'nurse-001');

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO operation_log
  (log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id)
VALUES
  ('op_phase_19_25_seed', 'admin-001', 'ADMIN', 'SEED_PHASE_19_25', 'DEMO_DATA', 'phase-19-25', JSON_OBJECT('ready', true), 'seed-phase-19-25')
ON DUPLICATE KEY UPDATE
  after_value = VALUES(after_value),
  trace_id = VALUES(trace_id);
