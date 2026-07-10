INSERT INTO sys_user (user_id, username, password_hash, display_name, phone, account_status) VALUES
('family-002', 'family_no_scope_demo', '{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au', 'No scope family', '13800000006', 'ENABLED');

INSERT INTO user_role (user_id, role_id) VALUES ('family-002', 'role_family');

INSERT INTO elder_family_binding (
  binding_id, elder_id, family_id, binding_status, scope_codes,
  relation_type, inviter_user_id, approver_user_id, remark
) VALUES (
  'binding_002', 'elder_001', 'family-002', 'ACTIVE',
  '["HEALTH_VIEW"]', 'OTHER', 'family-002', 'elder-001', 'No report confirm scope'
);

INSERT INTO nursing_order (
  order_id, elder_id, family_id, service_id, address_id, order_status,
  scheduled_start_at, service_price_cent, contact_name, contact_phone, created_by
) VALUES
('order_ack_elder', 'elder_001', 'family-001', 'service_001', 'address_001', 'WAIT_CONFIRM', TIMESTAMP '2026-07-10 09:00:00', 19900, 'Contact', '13800000002', 'family-001'),
('order_ack_family', 'elder_001', 'family-001', 'service_001', 'address_001', 'WAIT_CONFIRM', TIMESTAMP '2026-07-10 09:00:00', 19900, 'Contact', '13800000002', 'family-001'),
('order_archive', 'elder_001', 'family-001', 'service_001', 'address_001', 'WAIT_CONFIRM', TIMESTAMP '2026-07-10 09:00:00', 19900, 'Contact', '13800000002', 'family-001'),
('order_forbidden', 'elder_001', 'family-002', 'service_001', 'address_001', 'WAIT_CONFIRM', TIMESTAMP '2026-07-10 09:00:00', 19900, 'Contact', '13800000006', 'family-002');

INSERT INTO service_report (report_id, order_id, report_status, summary, generated_by) VALUES
('report_ack_elder', 'order_ack_elder', 'WAIT_CONFIRM', 'Elder ack report', 'nurse-001'),
('report_ack_family', 'order_ack_family', 'WAIT_CONFIRM', 'Family ack report', 'nurse-001'),
('report_archive', 'order_archive', 'WAIT_CONFIRM', 'Archive decision report', 'nurse-001'),
('report_forbidden', 'order_forbidden', 'WAIT_CONFIRM', 'Forbidden report', 'nurse-001');

INSERT INTO health_info_review_task (
  review_task_id, report_id, order_id, elder_id, field_name, old_value, new_value,
  source_type, source_id, review_status, created_by
) VALUES
('review_accept', 'report_archive', 'order_archive', 'elder_001', 'healthSummary', 'old', 'accepted value', 'REPORT_ACK', 'report_archive', 'PENDING', 'family-001'),
('review_reject', 'report_archive', 'order_archive', 'elder_001', 'careLevel', 'LEVEL_2', 'LEVEL_3', 'REPORT_ACK', 'report_archive', 'PENDING', 'family-001');
