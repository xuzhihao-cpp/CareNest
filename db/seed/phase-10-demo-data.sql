USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO nursing_order (
  order_id, elder_id, family_id, service_id, address_id, order_status,
  scheduled_start_at, scheduled_end_at, service_price_cent,
  contact_name, contact_phone, remark, created_by
) VALUES (
  'order_001', 'elder_001', 'family-001', 'service_001', 'address_001', 'WAIT_DISPATCH',
  '2026-07-10 09:00:00', '2026-07-10 10:00:00', 19900,
  '张小明', '13800000002', '阶段10预约下单演示订单', 'family-001'
) ON DUPLICATE KEY UPDATE
  elder_id = VALUES(elder_id),
  family_id = VALUES(family_id),
  service_id = VALUES(service_id),
  address_id = VALUES(address_id),
  order_status = VALUES(order_status),
  scheduled_start_at = VALUES(scheduled_start_at),
  scheduled_end_at = VALUES(scheduled_end_at),
  service_price_cent = VALUES(service_price_cent),
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  remark = VALUES(remark),
  created_by = VALUES(created_by);

INSERT INTO order_status_log (
  status_log_id, order_id, from_status, to_status, changed_by, change_reason
) VALUES (
  'order_status_log_001', 'order_001', NULL, 'WAIT_DISPATCH', 'family-001', '演示订单创建'
) ON DUPLICATE KEY UPDATE
  from_status = VALUES(from_status),
  to_status = VALUES(to_status),
  changed_by = VALUES(changed_by),
  change_reason = VALUES(change_reason);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, before_value, after_value, trace_id
) VALUES (
  'op_seed_010', 'admin-001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-10',
  NULL, JSON_OBJECT('orderStatus', 'WAIT_DISPATCH'), 'seed-phase-10'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);
