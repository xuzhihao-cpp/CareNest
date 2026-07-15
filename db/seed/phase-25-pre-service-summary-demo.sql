USE smart_nursing;
SET NAMES utf8mb4;

-- Isolated acceptance task for the Phase 25 read-only pre-service workflow.
INSERT IGNORE INTO sys_user (
  user_id, username, password_hash, display_name, phone, account_status
)
SELECT 'phase25-nurse-002', 'phase25_nurse_demo', password_hash,
       '阶段25未派单护理账号', '13800000025', 'ENABLED'
FROM sys_user WHERE user_id = 'nurse-001';

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT 'phase25-nurse-002', role_id FROM sys_role WHERE role_code = 'NURSE';

INSERT IGNORE INTO nursing_order (
  order_id, elder_id, family_id, service_id, service_address_snapshot,
  order_status, scheduled_start_at, scheduled_end_at, service_price_cent,
  contact_name, contact_phone, remark, created_by
) VALUES (
  'phase25_order_001', 'elder_001', 'family-001', 'service_001', '长沙市阶段25联调地址',
  'ACCEPTED', '2026-07-20 09:00:00', '2026-07-20 10:00:00', 12000,
  '家属演示账号', '13800000002', '阶段25服务前健康摘要联调专用', 'family-001'
);

INSERT IGNORE INTO nurse_task (
  task_id, order_id, nurse_id, task_status, dispatch_remark, accepted_at
) VALUES (
  'phase25_task_001', 'phase25_order_001', 'nurse-001', 'ACCEPTED',
  '阶段25服务前健康摘要联调专用', '2026-07-15 09:00:00'
);
