USE smart_nursing;
SET NAMES utf8mb4;

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
  'elder_001', 'elder-001', '张爷爷', 'MALE', '1946-05-12', 'LEVEL_2',
  '张小明', '13800000002', '高血压，需定期测量血压'
) ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  elder_name = VALUES(elder_name),
  gender = VALUES(gender),
  birth_date = VALUES(birth_date),
  care_level = VALUES(care_level),
  emergency_contact_name = VALUES(emergency_contact_name),
  emergency_contact_phone = VALUES(emergency_contact_phone),
  health_summary = VALUES(health_summary);

INSERT INTO elder_contact (contact_id, elder_id, contact_name, contact_phone, relation_type, is_primary) VALUES
('contact_001', 'elder_001', '张小明', '13800000002', 'SON', 1)
ON DUPLICATE KEY UPDATE
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  relation_type = VALUES(relation_type),
  is_primary = VALUES(is_primary);

INSERT INTO elder_family_binding (
  binding_id, elder_id, family_id, binding_status, scope_codes,
  relation_type, inviter_user_id, approver_user_id, remark
) VALUES (
  'binding_001', 'elder_001', 'family-001', 'ACTIVE',
  JSON_ARRAY('HEALTH_VIEW','HEALTH_EDIT','ORDER_CREATE','REPORT_VIEW','REPORT_CONFIRM'),
  'SON', 'family-001', 'elder-001', '演示绑定关系'
) ON DUPLICATE KEY UPDATE
  binding_status = VALUES(binding_status),
  scope_codes = VALUES(scope_codes),
  relation_type = VALUES(relation_type),
  inviter_user_id = VALUES(inviter_user_id),
  approver_user_id = VALUES(approver_user_id);

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
  'address_001', 'elder_001', 'family-001', '张小明', '13800000002',
  '310000', '310100', '310101', '人民路100号1单元201', 1
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
  'op_seed_006_009', 'admin-001', 'ADMIN', 'SEED_INIT', 'DEMO_DATA', 'phase-06-09',
  NULL, JSON_OBJECT('status', 'initialized'), 'seed-phase-06-09'
) ON DUPLICATE KEY UPDATE after_value = VALUES(after_value), trace_id = VALUES(trace_id);

DELETE FROM login_session
WHERE user_id IN ('user_elder_001', 'user_family_001', 'user_nurse_001', 'user_admin_001', 'user_cs_001');

DELETE FROM user_role
WHERE user_id IN ('user_elder_001', 'user_family_001', 'user_nurse_001', 'user_admin_001', 'user_cs_001');

DELETE FROM sys_user
WHERE user_id IN ('user_elder_001', 'user_family_001', 'user_nurse_001', 'user_admin_001', 'user_cs_001')
  AND user_id NOT IN (SELECT user_id FROM elder_profile);
