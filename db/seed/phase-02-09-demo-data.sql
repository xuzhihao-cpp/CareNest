USE smart_nursing;
SET NAMES utf8mb4;

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
('perm_admin_order_view', 'admin:order:view', '管理端查看订单', 'admin', 1),
('perm_service_item_manage', 'service-item:manage', '维护服务项目', 'service', 1),
('perm_elder_profile_edit', 'elder-profile:edit', '维护长辈档案', 'elder', 1),
('perm_binding_manage', 'binding:manage', '维护绑定授权', 'binding', 1)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), permission_group = VALUES(permission_group), enabled = VALUES(enabled);

INSERT IGNORE INTO role_permission (role_id, permission_id) VALUES
('role_elder', 'perm_auth_me'),
('role_family', 'perm_auth_me'),
('role_family', 'perm_elder_profile_edit'),
('role_family', 'perm_binding_manage'),
('role_nurse', 'perm_auth_me'),
('role_admin', 'perm_auth_me'),
('role_admin', 'perm_admin_order_view'),
('role_admin', 'perm_service_item_manage'),
('role_customer_service', 'perm_auth_me'),
('role_customer_service', 'perm_admin_order_view');

INSERT INTO authorization_scope (scope_code, scope_name, enabled, sort) VALUES
('HEALTH_VIEW', '查看健康档案', 1, 1),
('HEALTH_EDIT', '编辑健康档案', 1, 2),
('ORDER_CREATE', '创建护理订单', 1, 3),
('REPORT_VIEW', '查看服务报告', 1, 4),
('REPORT_CONFIRM', '确认服务报告', 1, 5),
('ARCHIVE_EDIT', '编辑归档信息', 1, 6)
ON DUPLICATE KEY UPDATE scope_name = VALUES(scope_name), enabled = VALUES(enabled), sort = VALUES(sort);

INSERT INTO elder_profile (
  elder_id, user_id, elder_name, gender, birth_date, care_level,
  emergency_contact_name, emergency_contact_phone, health_summary
) VALUES (
  'elder_001', 'user_elder_001', '张爷爷', 'MALE', '1946-05-12', 'LEVEL_2',
  '张小明', '13800000002', '高血压，需定期测量血压'
) ON DUPLICATE KEY UPDATE
  elder_name = VALUES(elder_name),
  emergency_contact_name = VALUES(emergency_contact_name),
  emergency_contact_phone = VALUES(emergency_contact_phone),
  health_summary = VALUES(health_summary);

INSERT INTO elder_contact (contact_id, elder_id, contact_name, contact_phone, relation_type, is_primary) VALUES
('contact_001', 'elder_001', '张小明', '13800000002', 'SON', 1)
ON DUPLICATE KEY UPDATE contact_name = VALUES(contact_name), contact_phone = VALUES(contact_phone), is_primary = VALUES(is_primary);

INSERT INTO elder_family_binding (
  binding_id, elder_id, family_id, binding_status, scope_codes,
  relation_type, inviter_user_id, approver_user_id, remark
) VALUES (
  'binding_001', 'elder_001', 'family_001', 'ACTIVE',
  JSON_ARRAY('HEALTH_VIEW','HEALTH_EDIT','ORDER_CREATE','REPORT_VIEW','REPORT_CONFIRM'),
  'SON', 'user_family_001', 'user_elder_001', '演示绑定关系'
) ON DUPLICATE KEY UPDATE
  binding_status = VALUES(binding_status),
  scope_codes = VALUES(scope_codes),
  relation_type = VALUES(relation_type);

INSERT INTO service_item (
  service_id, service_name, service_desc, price_cent, duration_minutes, service_status, sort
) VALUES
('service_001', '基础上门护理', '生命体征测量、基础照护和服务记录', 19900, 60, 'ON_SHELF', 1),
('service_002', '康复陪护', '康复训练陪护、风险观察和家属反馈', 29900, 90, 'ON_SHELF', 2)
ON DUPLICATE KEY UPDATE
  service_name = VALUES(service_name),
  service_desc = VALUES(service_desc),
  price_cent = VALUES(price_cent),
  duration_minutes = VALUES(duration_minutes),
  service_status = VALUES(service_status),
  sort = VALUES(sort);

INSERT INTO service_address (
  address_id, elder_id, family_id, contact_name, contact_phone,
  province_code, city_code, region_code, detail_address, is_default
) VALUES (
  'address_001', 'elder_001', 'family_001', '张小明', '13800000002',
  '310000', '310100', '310101', '人民路 100 号 1 单元 201', 1
) ON DUPLICATE KEY UPDATE
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  province_code = VALUES(province_code),
  city_code = VALUES(city_code),
  region_code = VALUES(region_code),
  detail_address = VALUES(detail_address),
  is_default = VALUES(is_default);

INSERT INTO operation_log (
  log_id, operator_id, role_code, operation_type, biz_type, biz_id, before_value, after_value, trace_id
) VALUES (
  'op_seed_001', 'user_admin_001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-02-09',
  NULL, JSON_OBJECT('status', 'initialized'), 'seed-phase-02-09'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);
