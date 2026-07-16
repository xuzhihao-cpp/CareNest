INSERT INTO sys_role (role_id, role_code, role_name, enabled) VALUES
('role_elder', 'ELDER', '长辈', TRUE),
('role_family', 'FAMILY', '家属', TRUE),
('role_nurse', 'NURSE', '护理人员', TRUE),
('role_admin', 'ADMIN', '管理员', TRUE),
('role_customer_service', 'CUSTOMER_SERVICE', '客服', TRUE);

INSERT INTO sys_user (user_id, username, password_hash, display_name, phone, account_status) VALUES
('elder-001', 'elder_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '长辈演示账号', '13800000001', 'ENABLED'),
('elder-002', 'elder_other_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '其他长辈账号', '13800000006', 'ENABLED'),
('family-001', 'family_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '家属演示账号', '13800000002', 'ENABLED'),
('nurse-001', 'nurse_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '护理演示账号', '13800000003', 'ENABLED'),
('admin-001', 'admin_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '管理员演示账号', '13800000004', 'ENABLED'),
('cs-001', 'cs_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', '客服演示账号', '13800000005', 'ENABLED');

INSERT INTO user_role (user_id, role_id) VALUES
('elder-001', 'role_elder'),
('elder-002', 'role_elder'),
('family-001', 'role_family'),
('nurse-001', 'role_nurse'),
('admin-001', 'role_admin'),
('cs-001', 'role_customer_service');

INSERT INTO sys_permission (permission_id, permission_code, permission_name, permission_group, enabled) VALUES
('perm_elder_reminder_view', 'ELDER_REMINDER_VIEW', '查看提醒', 'elder', TRUE),
('perm_elder_ai_chat', 'ELDER_AI_CHAT', '使用AI聊天', 'elder', TRUE),
('perm_family_elder_view', 'FAMILY_ELDER_VIEW', '查看长辈信息', 'family', TRUE),
('perm_family_order_create', 'FAMILY_ORDER_CREATE', '创建护理订单', 'family', TRUE),
('perm_nurse_order_view', 'NURSE_ORDER_VIEW', '查看护理订单', 'nurse', TRUE),
('perm_nurse_report_create', 'NURSE_REPORT_CREATE', '创建服务报告', 'nurse', TRUE),
('perm_nurse_appeal_create', 'NURSE_APPEAL_CREATE', '提交申诉', 'nurse', TRUE),
('perm_admin_dashboard_view', 'ADMIN_DASHBOARD_VIEW', '查看管理看板', 'admin', TRUE),
('perm_role_permission_manage', 'ROLE_PERMISSION_MANAGE', '管理角色权限', 'permission', TRUE),
('perm_cs_ticket_handle', 'CUSTOMER_SERVICE_TICKET_HANDLE', '处理客服工单', 'customer_service', TRUE);

INSERT INTO role_permission (role_id, permission_id, sort) VALUES
('role_elder', 'perm_elder_reminder_view', 1),
('role_elder', 'perm_elder_ai_chat', 2),
('role_family', 'perm_family_elder_view', 1),
('role_family', 'perm_family_order_create', 2),
('role_nurse', 'perm_nurse_order_view', 1),
('role_nurse', 'perm_nurse_report_create', 2),
('role_admin', 'perm_admin_dashboard_view', 1),
('role_admin', 'perm_role_permission_manage', 2),
('role_customer_service', 'perm_cs_ticket_handle', 1),
('role_customer_service', 'perm_role_permission_manage', 2);

INSERT INTO authorization_scope (scope_code, scope_name, enabled, sort) VALUES
('HEALTH_VIEW', '查看健康档案', TRUE, 1),
('HEALTH_EDIT', '编辑健康档案', TRUE, 2),
('ORDER_CREATE', '创建护理订单', TRUE, 3),
('REPORT_VIEW', '查看服务报告', TRUE, 4),
('REPORT_CONFIRM', '确认服务报告', TRUE, 5),
('ARCHIVE_EDIT', '编辑归档信息', TRUE, 6);

INSERT INTO elder_profile (
  elder_id, user_id, elder_name, gender, birth_date, care_level,
  emergency_contact_name, emergency_contact_phone, health_summary
) VALUES (
  'elder_001', 'elder-001', '张爷爷', 'MALE', DATE '1946-05-12', 'LEVEL_2',
  '张小明', '13800000002', '高血压，需定期测量血压'
);

INSERT INTO reminder_task (task_id, elder_id, reminder_type, source_type, source_id, title, content, scheduled_at, reminder_status, created_by)
VALUES ('reminder_001', 'elder_001', 'MEDICATION', 'MEDICATION_PLAN', 'medication_001', '早晨用药', '请按照照护计划服药。', CURRENT_TIMESTAMP, 'PENDING', 'family-001');

INSERT INTO elder_contact (contact_id, elder_id, contact_name, contact_phone, relation_type, is_primary) VALUES
('contact_001', 'elder_001', '张小明', '13800000002', 'SON', TRUE);

INSERT INTO elder_profile (
  elder_id, user_id, elder_name, gender, birth_date, care_level,
  emergency_contact_name, emergency_contact_phone, health_summary
) VALUES (
  'elder_002', 'elder-002', '李爷爷', 'MALE', DATE '1942-11-02', 'LEVEL_1',
  '李芳', '13800000006', '无'
);

INSERT INTO elder_family_binding (
  binding_id, elder_id, family_id, binding_status, scope_codes,
  relation_type, inviter_user_id, approver_user_id, remark
) VALUES (
  'binding_001', 'elder_001', 'family-001', 'ACTIVE',
  '["HEALTH_VIEW","HEALTH_EDIT","ORDER_CREATE","REPORT_VIEW","REPORT_CONFIRM","ARCHIVE_EDIT"]',
  'SON', 'family-001', 'elder-001', '演示绑定关系'
);

INSERT INTO elder_family_binding (
  binding_id, elder_id, family_id, binding_status, scope_codes,
  relation_type, inviter_user_id, approver_user_id, remark
) VALUES (
  'binding_other_elder', 'elder_002', 'family-001', 'PENDING',
  '["HEALTH_VIEW"]', 'OTHER', 'family-001', NULL, '隔离测试绑定'
);

INSERT INTO service_address (
  address_id, elder_id, family_id, contact_name, contact_phone,
  province_code, city_code, region_code, detail_address, is_default
) VALUES (
  'address_001', 'elder_001', 'family-001', '张小明', '13800000002',
  '310000', '310100', '310101', '人民路100号1单元201', TRUE
);

INSERT INTO health_archive (
  archive_id, elder_id, archive_version, care_summary, updated_by
) VALUES (
  'archive_001', 'elder_001', 1, '血压需持续观察，服务前确认用药和过敏情况。', 'family-001'
);

INSERT INTO chronic_disease (
  disease_id, elder_id, disease_name, disease_status, diagnosed_at, remark
) VALUES (
  'disease_001', 'elder_001', '高血压', 'ACTIVE', DATE '2022-03-01', '定期测量血压'
);

INSERT INTO medication_plan (
  medication_id, elder_id, medication_name, dosage, frequency, time_points,
  start_date, end_date, medication_status, remark
) VALUES (
  'med_001', 'elder_001', '降压药', '1片', 'ONCE_DAILY', '["08:00"]',
  DATE '2024-01-01', NULL, 'ACTIVE', '遵医嘱记录'
);

INSERT INTO allergy_record (
  allergy_id, elder_id, allergen, reaction, severity, remark
) VALUES (
  'allergy_001', 'elder_001', '青霉素', '皮疹', 'SEVERE', '护理前核对'
);

INSERT INTO risk_tag (
  risk_tag_id, elder_id, tag_code, tag_name, risk_level, remark
) VALUES (
  'risk_001', 'elder_001', 'FALL_RISK', '跌倒风险', 'MEDIUM', '移动时注意搀扶'
);

INSERT INTO care_plan (
  care_plan_id, elder_id, plan_content, plan_status
) VALUES (
  'plan_001', 'elder_001',
  '{"careGoals":"保持血压稳定","dailyCare":"每日测量血压","precautions":"避免突然起身"}',
  'ACTIVE'
);
