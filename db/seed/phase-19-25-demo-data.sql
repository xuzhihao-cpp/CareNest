USE smart_nursing;
SET NAMES utf8mb4;

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
  ('med_001', 'elder_001', '降压药', '1片', '每日一次', JSON_ARRAY('08:00'), '2024-01-01', 'ACTIVE', '仅作记录，具体用药以医生医嘱为准');

DELETE FROM allergy_record WHERE elder_id = 'elder_001';
INSERT INTO allergy_record
  (allergy_id, elder_id, allergen, reaction, severity, remark)
VALUES
  ('allergy_001', 'elder_001', '青霉素', '皮疹', 'HIGH', '护理前需主动核对');

DELETE FROM risk_tag WHERE elder_id = 'elder_001';
INSERT INTO risk_tag
  (risk_tag_id, elder_id, tag_name, risk_level, remark)
VALUES
  ('risk_001', 'elder_001', '跌倒风险', 'MEDIUM', '上下楼和洗浴时需搀扶');

DELETE FROM care_plan WHERE elder_id = 'elder_001';
INSERT INTO care_plan
  (care_plan_id, elder_id, plan_content, plan_status)
VALUES
  ('plan_001', 'elder_001', '服务前确认血压、过敏史和当天用药情况，护理过程中避免长时间站立。', 'ACTIVE');

DELETE FROM medical_file WHERE medical_file_id IN ('medical_file_001', 'medical_file_002');
DELETE FROM file_asset WHERE file_id IN ('file_001', 'file_002');
INSERT INTO file_asset
  (file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by)
VALUES
  ('file_001', 'blood-pressure-report.pdf', 'application/pdf', 204800, 'carenest-medical', 'demo/elder_001/blood-pressure-report.pdf', 'APPROVED', 'family-001'),
  ('file_002', 'lab-result.pdf', 'application/pdf', 102400, 'carenest-medical', 'demo/elder_001/lab-result.pdf', 'PENDING', 'family-001');

INSERT INTO medical_file
  (medical_file_id, elder_id, file_id, file_type, title, occurred_at, audit_status, review_comment, uploader_id, reviewer_id, reviewed_at)
VALUES
  ('medical_file_001', 'elder_001', 'file_001', 'CHECK_REPORT', '近期血压检查报告', '2026-07-01', 'APPROVED', '资料清晰，可用于服务前摘要。', 'family-001', 'admin-001', CURRENT_TIMESTAMP),
  ('medical_file_002', 'elder_001', 'file_002', 'CHECK_REPORT', '待审核化验单', '2026-07-05', 'PENDING', NULL, 'family-001', NULL, NULL);

DELETE FROM elder_health_feedback WHERE feedback_id IN ('feedback_001');
INSERT INTO elder_health_feedback
  (feedback_id, elder_id, feedback_type, severity, content, input_type, created_by)
VALUES
  ('feedback_001', 'elder_001', 'DIZZINESS', 'MEDIUM', '今天起身时有些头晕，已坐下休息。', 'TEXT', 'elder-001');

DELETE FROM health_update_suggestion WHERE suggestion_id = 'suggestion_001';
DELETE FROM health_info_review_task WHERE review_task_id = 'review_task_019_001';
INSERT INTO health_update_suggestion
  (suggestion_id, elder_id, order_id, field_name, old_value, new_value, source_type, source_id, reason, suggestion_status, created_by, review_task_id)
VALUES
  ('suggestion_001', 'elder_001', 'order_001', 'riskTags', '跌倒风险：MEDIUM', '夜间跌倒风险：HIGH', 'SERVICE_RECORD', 'service_record_001', '护理记录提示夜间起身不稳，需要管理员审核后归档。', 'PENDING', 'nurse-001', 'review_task_019_001');

INSERT INTO health_info_review_task
  (review_task_id, suggestion_id, task_type, order_id, elder_id, field_name, old_value, new_value, source_type, source_id, review_status, created_by)
VALUES
  ('review_task_019_001', 'suggestion_001', 'HEALTH_UPDATE', 'order_001', 'elder_001', 'riskTags', '跌倒风险：MEDIUM', '夜间跌倒风险：HIGH', 'SUGGESTION', 'suggestion_001', 'PENDING', 'nurse-001');

INSERT INTO operation_log
  (log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id)
VALUES
  ('op_phase_19_25_seed', 'admin-001', 'ADMIN', 'SEED_PHASE_19_25', 'DEMO_DATA', 'phase-19-25', JSON_OBJECT('ready', true), 'seed-phase-19-25')
ON DUPLICATE KEY UPDATE
  after_value = VALUES(after_value),
  trace_id = VALUES(trace_id);
