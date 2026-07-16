INSERT INTO service_item VALUES ('service_1', '基础护理', 'ON_SHELF');
INSERT INTO nursing_order VALUES ('order_1', 'service_1', 'elder_1', 'family_1', 'ACCEPTED');
INSERT INTO nurse_task VALUES ('task_1', 'order_1', 'nurse_1', 'ACCEPTED');
INSERT INTO file_asset VALUES
  ('file_1', 'nurse_1', 'image/jpeg'),
  ('file_2', 'nurse_1', 'application/pdf'),
  ('file_other', 'nurse_2', 'image/jpeg');
INSERT INTO elder_family_binding VALUES
  ('binding_1', 'elder_1', 'family_1', 'ACTIVE', '["REPORT_VIEW"]');
INSERT INTO sys_role VALUES
  ('role_admin', 'ADMIN', 1),
  ('role_nurse', 'NURSE', 1),
  ('role_family', 'FAMILY', 1);
INSERT INTO user_role VALUES
  ('admin_1', 'role_admin'),
  ('nurse_1', 'role_nurse'),
  ('nurse_2', 'role_nurse'),
  ('family_1', 'role_family');
INSERT INTO sys_permission VALUES
  ('perm_config', 'CARE_METRIC_CONFIG_MANAGE', 1),
  ('perm_review', 'CARE_EVIDENCE_REVIEW', 1);
INSERT INTO role_permission VALUES
  ('role_admin', 'perm_config'),
  ('role_admin', 'perm_review');
