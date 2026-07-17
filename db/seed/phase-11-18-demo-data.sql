USE smart_nursing;
SET NAMES utf8mb4;

DELETE FROM care_report_ack WHERE ack_id = 'ack_001';
DELETE FROM health_info_review_task WHERE review_task_id = 'review_task_001';
DELETE FROM complaint
WHERE review_id IN (
  SELECT review_id FROM review WHERE report_id = 'report_001'
);
DELETE FROM review WHERE report_id = 'report_001';
DELETE FROM service_report_item WHERE item_id IN ('report_item_001', 'report_item_002', 'report_item_003');
DELETE FROM service_report WHERE report_id = 'report_001';
DELETE FROM vital_sign_record WHERE vital_id = 'vital_001';
DELETE FROM care_service_record WHERE record_id = 'service_record_001';
DELETE FROM nurse_task WHERE task_id = 'task_001';
DELETE FROM order_status_log WHERE status_log_id IN (
  'order_status_log_012',
  'order_status_log_013',
  'order_status_log_014',
  'order_status_log_015',
  'order_status_log_016',
  'order_status_log_017',
  'order_status_log_018'
);

UPDATE nursing_order
SET order_status = 'COMPLETED',
    scheduled_start_at = '2026-07-10 09:00:00',
    scheduled_end_at = '2026-07-10 10:00:00'
WHERE order_id = 'order_001';

INSERT INTO nurse_task (
  task_id, order_id, nurse_id, task_status, dispatch_remark,
  accepted_at, started_at, completed_at
) VALUES (
  'task_001', 'order_001', 'nurse-001', 'COMPLETED', '阶段12演示派单',
  '2026-07-10 08:20:00', '2026-07-10 09:00:00', '2026-07-10 10:00:00'
) ON DUPLICATE KEY UPDATE
  nurse_id = VALUES(nurse_id),
  task_status = VALUES(task_status),
  dispatch_remark = VALUES(dispatch_remark),
  accepted_at = VALUES(accepted_at),
  started_at = VALUES(started_at),
  completed_at = VALUES(completed_at);

INSERT INTO care_service_record (
  record_id, order_id, task_id, nurse_id, start_time, end_time,
  content, nursing_advice, abnormal_flag, created_by
) VALUES (
  'service_record_001', 'order_001', 'task_001', 'nurse-001',
  '2026-07-10 09:00:00', '2026-07-10 10:00:00',
  '完成基础上门护理、生命体征测量和用药提醒。',
  '建议继续每日监测血压，保持低盐饮食。',
  0, 'nurse-001'
) ON DUPLICATE KEY UPDATE
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  content = VALUES(content),
  nursing_advice = VALUES(nursing_advice),
  abnormal_flag = VALUES(abnormal_flag),
  created_by = VALUES(created_by);

INSERT INTO vital_sign_record (
  vital_id, order_id, task_id, nurse_id, measured_at,
  temperature, pulse, breath_rate, systolic_pressure, diastolic_pressure,
  blood_oxygen, remark
) VALUES (
  'vital_001', 'order_001', 'task_001', 'nurse-001', '2026-07-10 09:20:00',
  36.6, 78, 18, 128, 82, 98, '阶段14演示生命体征记录'
) ON DUPLICATE KEY UPDATE
  measured_at = VALUES(measured_at),
  temperature = VALUES(temperature),
  pulse = VALUES(pulse),
  breath_rate = VALUES(breath_rate),
  systolic_pressure = VALUES(systolic_pressure),
  diastolic_pressure = VALUES(diastolic_pressure),
  blood_oxygen = VALUES(blood_oxygen),
  remark = VALUES(remark);

INSERT INTO service_report (
  report_id, order_id, report_status, summary, nursing_advice,
  generated_by, generated_at, confirmed_at
) VALUES (
  'report_001', 'order_001', 'CONFIRMED',
  '阶段15演示服务报告：护理服务已完成，生命体征稳定。',
  '建议家属继续关注血压记录并按医嘱用药。',
  'nurse-001', '2026-07-10 10:15:00', '2026-07-10 10:30:00'
) ON DUPLICATE KEY UPDATE
  report_status = VALUES(report_status),
  summary = VALUES(summary),
  nursing_advice = VALUES(nursing_advice),
  generated_by = VALUES(generated_by),
  generated_at = VALUES(generated_at),
  confirmed_at = VALUES(confirmed_at);

INSERT INTO service_report_item (
  item_id, report_id, item_type, item_title, item_content, source_id, sort
) VALUES
('report_item_001', 'report_001', 'SERVICE_RECORD', '服务记录', '基础护理和用药提醒已完成。', 'service_record_001', 1),
('report_item_002', 'report_001', 'VITAL_SIGN', '生命体征', '体温36.6，血压128/82，血氧98%。', 'vital_001', 2),
('report_item_003', 'report_001', 'NURSING_ADVICE', '护理建议', '继续每日监测血压并保持低盐饮食。', 'service_record_001', 3)
ON DUPLICATE KEY UPDATE
  item_type = VALUES(item_type),
  item_title = VALUES(item_title),
  item_content = VALUES(item_content),
  source_id = VALUES(source_id),
  sort = VALUES(sort);

INSERT INTO health_info_review_task (
  review_task_id, report_id, order_id, elder_id, field_name,
  old_value, new_value, source_type, source_id, review_status, created_by
) VALUES (
  'review_task_001', 'report_001', 'order_001', 'elder_001', 'healthSummary',
  '高血压，需要定期测量血压',
  '高血压，需要每日测量血压并记录',
  'REPORT_ACK', 'ack_001', 'PENDING', 'family-001'
) ON DUPLICATE KEY UPDATE
  report_id = VALUES(report_id),
  order_id = VALUES(order_id),
  elder_id = VALUES(elder_id),
  field_name = VALUES(field_name),
  old_value = VALUES(old_value),
  new_value = VALUES(new_value),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  review_status = VALUES(review_status),
  created_by = VALUES(created_by);

INSERT INTO care_report_ack (
  ack_id, report_id, order_id, ack_user_id, ack_role, ack_result,
  satisfaction, remark, accepted_suggestion_ids
) VALUES (
  'ack_001', 'report_001', 'order_001', 'family-001', 'FAMILY', 'ACCEPTED',
  5, '家属确认服务完成并采纳健康归档建议。', JSON_ARRAY('review_task_001')
) ON DUPLICATE KEY UPDATE
  ack_user_id = VALUES(ack_user_id),
  ack_role = VALUES(ack_role),
  ack_result = VALUES(ack_result),
  satisfaction = VALUES(satisfaction),
  remark = VALUES(remark),
  accepted_suggestion_ids = VALUES(accepted_suggestion_ids);

INSERT INTO order_status_log (
  status_log_id, order_id, from_status, to_status, changed_by, change_reason, created_at
) VALUES
('order_status_log_012', 'order_001', 'WAIT_DISPATCH', 'DISPATCHED', 'admin-001', '阶段12派单', '2026-07-10 08:10:00'),
('order_status_log_013', 'order_001', 'DISPATCHED', 'ACCEPTED', 'nurse-001', '阶段12护理人员接单', '2026-07-10 08:20:00'),
('order_status_log_014', 'order_001', 'ACCEPTED', 'ON_THE_WAY', 'nurse-001', '阶段13护理人员出发', '2026-07-10 08:40:00'),
('order_status_log_015', 'order_001', 'ON_THE_WAY', 'SERVING', 'nurse-001', '阶段14开始服务', '2026-07-10 09:00:00'),
('order_status_log_016', 'order_001', 'SERVING', 'WAIT_REPORT', 'nurse-001', '阶段14提交服务记录', '2026-07-10 10:00:00'),
('order_status_log_017', 'order_001', 'WAIT_REPORT', 'WAIT_CONFIRM', 'nurse-001', '阶段15生成服务报告', '2026-07-10 10:15:00'),
('order_status_log_018', 'order_001', 'WAIT_CONFIRM', 'COMPLETED', 'family-001', '阶段16家属确认服务报告', '2026-07-10 10:30:00')
ON DUPLICATE KEY UPDATE
  from_status = VALUES(from_status),
  to_status = VALUES(to_status),
  changed_by = VALUES(changed_by),
  change_reason = VALUES(change_reason),
  created_at = VALUES(created_at);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, before_value, after_value, trace_id
) VALUES (
  'op_seed_011_018', 'admin-001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-11-18',
  NULL, JSON_OBJECT('flowStatus', 'COMPLETED', 'orderId', 'order_001'), 'seed-phase-11-18'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);
