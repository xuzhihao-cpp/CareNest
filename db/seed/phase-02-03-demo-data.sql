USE smart_nursing;
SET NAMES utf8mb4;

DELETE FROM role_permission
WHERE permission_id IN (
      'perm_elder_reminder_view', 'perm_elder_ai_chat',
      'perm_family_elder_view', 'perm_family_order_create',
      'perm_nurse_order_view', 'perm_nurse_report_create', 'perm_nurse_appeal_create',
      'perm_admin_dashboard_view', 'perm_role_permission_manage',
      'perm_cs_ticket_handle'
   );
DELETE FROM sys_permission
WHERE permission_id IN (
  'perm_elder_reminder_view', 'perm_elder_ai_chat',
  'perm_family_elder_view', 'perm_family_order_create',
  'perm_nurse_order_view', 'perm_nurse_report_create', 'perm_nurse_appeal_create',
  'perm_admin_dashboard_view', 'perm_role_permission_manage',
  'perm_cs_ticket_handle'
);
DELETE FROM user_role
WHERE user_id IN (
  'elder-001', 'family-001', 'nurse-001', 'admin-001', 'cs-001',
  'user_elder_001', 'user_family_001', 'user_nurse_001', 'user_admin_001', 'user_cs_001'
);
DELETE FROM login_session
WHERE expire_at <= CURRENT_TIMESTAMP
   OR user_id IN (
     'user_elder_001', 'user_family_001', 'user_nurse_001', 'user_admin_001', 'user_cs_001'
   );
DELETE FROM operation_log WHERE biz_id = 'phase-02-03';
INSERT INTO sys_role (role_id, role_code, role_name, enabled) VALUES
('role_elder', 'ELDER', '长辈', 1),
('role_family', 'FAMILY', '家属', 1),
('role_nurse', 'NURSE', '护理人员', 1),
('role_admin', 'ADMIN', '管理员', 1),
('role_customer_service', 'CUSTOMER_SERVICE', '客服', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), enabled = VALUES(enabled);

INSERT INTO sys_user (user_id, username, password_hash, display_name, phone, account_status) VALUES
('elder-001', 'elder_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '长辈演示账号', '13800000001', 'ENABLED'),
('family-001', 'family_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '家属演示账号', '13800000002', 'ENABLED'),
('nurse-001', 'nurse_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '护理演示账号', '13800000003', 'ENABLED'),
('admin-001', 'admin_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '管理员演示账号', '13800000004', 'ENABLED'),
('cs-001', 'cs_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '客服演示账号', '13800000005', 'ENABLED')
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  phone = VALUES(phone),
  account_status = VALUES(account_status);

INSERT INTO user_role (user_id, role_id) VALUES
('elder-001', 'role_elder'),
('family-001', 'role_family'),
('nurse-001', 'role_nurse'),
('admin-001', 'role_admin'),
('cs-001', 'role_customer_service')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_permission (permission_id, permission_code, permission_name, permission_group, enabled) VALUES
('perm_elder_reminder_view', 'ELDER_REMINDER_VIEW', '查看提醒', 'elder', 1),
('perm_elder_ai_chat', 'ELDER_AI_CHAT', '使用AI聊天', 'elder', 1),
('perm_family_elder_view', 'FAMILY_ELDER_VIEW', '查看长辈信息', 'family', 1),
('perm_family_order_create', 'FAMILY_ORDER_CREATE', '创建护理订单', 'family', 1),
('perm_nurse_order_view', 'NURSE_ORDER_VIEW', '查看护理订单', 'nurse', 1),
('perm_nurse_report_create', 'NURSE_REPORT_CREATE', '创建服务报告', 'nurse', 1),
('perm_nurse_appeal_create', 'NURSE_APPEAL_CREATE', '提交申诉', 'nurse', 1),
('perm_admin_dashboard_view', 'ADMIN_DASHBOARD_VIEW', '查看管理看板', 'admin', 1),
('perm_role_permission_manage', 'ROLE_PERMISSION_MANAGE', '管理角色权限', 'permission', 1),
('perm_cs_ticket_handle', 'CUSTOMER_SERVICE_TICKET_HANDLE', '处理客服工单', 'customer_service', 1)
ON DUPLICATE KEY UPDATE
  permission_code = VALUES(permission_code),
  permission_name = VALUES(permission_name),
  permission_group = VALUES(permission_group),
  enabled = VALUES(enabled);

INSERT INTO role_permission (role_id, permission_id, sort) VALUES
('role_elder', 'perm_elder_reminder_view', 1),
('role_elder', 'perm_elder_ai_chat', 2),
('role_family', 'perm_family_elder_view', 1),
('role_family', 'perm_family_order_create', 2),
('role_nurse', 'perm_nurse_order_view', 1),
('role_nurse', 'perm_nurse_report_create', 2),
('role_admin', 'perm_admin_dashboard_view', 1),
('role_admin', 'perm_role_permission_manage', 2),
('role_admin', 'perm_cs_ticket_handle', 3),
('role_customer_service', 'perm_cs_ticket_handle', 1),
('role_customer_service', 'perm_role_permission_manage', 2)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, before_value, after_value, trace_id
) VALUES (
  'op_seed_001', 'admin-001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-02-03',
  NULL, JSON_OBJECT('status', 'initialized'), 'seed-phase-02-03'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);
