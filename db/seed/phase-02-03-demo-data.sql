USE smart_nursing;
SET NAMES utf8mb4;

DELETE FROM role_permission
WHERE permission_id IN ('perm_service_item_manage', 'perm_elder_profile_edit', 'perm_binding_manage');

DELETE FROM sys_permission
WHERE permission_id IN ('perm_service_item_manage', 'perm_elder_profile_edit', 'perm_binding_manage');

INSERT INTO sys_role (role_id, role_code, role_name, enabled) VALUES
('role_elder', 'ELDER', '长辈', 1),
('role_family', 'FAMILY', '家属', 1),
('role_nurse', 'NURSE', '护理人员', 1),
('role_admin', 'ADMIN', '管理员', 1),
('role_customer_service', 'CUSTOMER_SERVICE', '客服', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), enabled = VALUES(enabled);

INSERT INTO sys_user (user_id, username, password_hash, display_name, phone, account_status) VALUES
('user_elder_001', 'elder001', '{bcrypt}$2b$10$YBiBCw9zV.3tjds.0sdspewkay2Z1x.2H8yBuVEZhoEsFKC4aawRG', '张爷爷', '13800000001', 'ENABLED'),
('user_family_001', 'family001', '{bcrypt}$2b$10$YBiBCw9zV.3tjds.0sdspewkay2Z1x.2H8yBuVEZhoEsFKC4aawRG', '张小明', '13800000002', 'ENABLED'),
('user_nurse_001', 'nurse001', '{bcrypt}$2b$10$YBiBCw9zV.3tjds.0sdspewkay2Z1x.2H8yBuVEZhoEsFKC4aawRG', '李护士', '13800000003', 'ENABLED'),
('user_admin_001', 'admin001', '{bcrypt}$2b$10$YBiBCw9zV.3tjds.0sdspewkay2Z1x.2H8yBuVEZhoEsFKC4aawRG', '平台管理员', '13800000004', 'ENABLED'),
('user_cs_001', 'cs001', '{bcrypt}$2b$10$YBiBCw9zV.3tjds.0sdspewkay2Z1x.2H8yBuVEZhoEsFKC4aawRG', '客服一号', '13800000005', 'ENABLED')
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  phone = VALUES(phone),
  account_status = VALUES(account_status);

INSERT IGNORE INTO user_role (user_id, role_id) VALUES
('user_elder_001', 'role_elder'),
('user_family_001', 'role_family'),
('user_nurse_001', 'role_nurse'),
('user_admin_001', 'role_admin'),
('user_cs_001', 'role_customer_service');

INSERT INTO sys_permission (permission_id, permission_code, permission_name, permission_group, enabled) VALUES
('perm_auth_me', 'auth:me', '查看当前用户', 'auth', 1),
('perm_auth_menu', 'auth:menu', '查看角色菜单', 'auth', 1),
('perm_admin_order_view', 'admin:order:view', '管理端查看订单', 'admin', 1),
('perm_role_permission_manage', 'role-permission:manage', '维护角色权限', 'permission', 1),
('perm_operation_log_view', 'operation-log:view', '查看操作日志', 'log', 1)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), permission_group = VALUES(permission_group), enabled = VALUES(enabled);

INSERT IGNORE INTO role_permission (role_id, permission_id) VALUES
('role_elder', 'perm_auth_me'),
('role_elder', 'perm_auth_menu'),
('role_family', 'perm_auth_me'),
('role_family', 'perm_auth_menu'),
('role_nurse', 'perm_auth_me'),
('role_nurse', 'perm_auth_menu'),
('role_admin', 'perm_auth_me'),
('role_admin', 'perm_auth_menu'),
('role_admin', 'perm_admin_order_view'),
('role_admin', 'perm_role_permission_manage'),
('role_admin', 'perm_operation_log_view'),
('role_customer_service', 'perm_auth_me'),
('role_customer_service', 'perm_auth_menu'),
('role_customer_service', 'perm_admin_order_view');

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, before_value, after_value, trace_id
) VALUES (
  'op_seed_001', 'user_admin_001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-02-03',
  NULL, JSON_OBJECT('status', 'initialized'), 'seed-phase-02-03'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);
