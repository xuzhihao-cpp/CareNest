INSERT INTO sys_user VALUES
  ('admin_1','admin_demo','ENABLED'),('nurse_1','nurse_demo','ENABLED'),
  ('family_1','family_demo','ENABLED'),('elder_user_1','elder_demo','ENABLED'),
  ('cs_1','cs_demo','ENABLED');
INSERT INTO sys_role VALUES
  ('role_admin','ADMIN',1),('role_nurse','NURSE',1),
  ('role_family','FAMILY',1),('role_elder','ELDER',1),
  ('role_cs','CUSTOMER_SERVICE',1);
INSERT INTO user_role VALUES
  ('admin_1','role_admin'),('nurse_1','role_nurse'),
  ('family_1','role_family'),('elder_user_1','role_elder'),('cs_1','role_cs');
INSERT INTO sys_permission VALUES
  ('p_follow','FOLLOW_UP_MANAGE',1),('p_complaint','COMPLAINT_HANDLE',1),
  ('p_appeal','NURSE_APPEAL_REVIEW',1),('p_article','TRAINING_ARTICLE_MANAGE',1),
  ('p_basic','DASHBOARD_BASIC_VIEW',1),('p_quality','DASHBOARD_QUALITY_VIEW',1),
  ('p_demo','DEMO_DATA_MANAGE',1);
INSERT INTO role_permission SELECT 'role_admin',permission_id FROM sys_permission;
INSERT INTO elder_profile VALUES ('elder_1','elder_user_1');
INSERT INTO elder_family_binding VALUES
  ('binding_1','elder_1','family_1','ACTIVE','["HEALTH_VIEW","REPORT_VIEW"]');
INSERT INTO service_item VALUES ('service_1','Basic care','ON_SHELF');
INSERT INTO nursing_order VALUES
  ('order_1','service_1','elder_1','family_1','COMPLETED',CURRENT_TIMESTAMP);
INSERT INTO nurse_task VALUES
  ('task_1','order_1','nurse_1','COMPLETED',CURRENT_TIMESTAMP);
INSERT INTO service_report VALUES ('report_1','order_1');
INSERT INTO file_asset VALUES ('file_family','family_1','image/jpeg');
INSERT INTO customer_service_ticket VALUES
  ('ticket_1','elder_1','family_1',NULL,'CONSULT','URGENT','PENDING',
   'Need follow up','MANUAL',NULL,NULL,CURRENT_TIMESTAMP);
INSERT INTO follow_up_record VALUES
  ('follow_seed','elder_1',NULL,'order_1','PHONE','Stable',NULL,0,NULL,'admin_1',CURRENT_TIMESTAMP);
INSERT INTO reminder_task VALUES
  ('reminder_1','elder_1','FOLLOW_UP','Follow up','Call family',CURRENT_TIMESTAMP,
   'DONE','FOLLOW_UP','follow_seed','admin_1',CURRENT_TIMESTAMP);
INSERT INTO reminder_record VALUES
  ('record_1','reminder_1','elder_1','DONE',CURRENT_TIMESTAMP);
INSERT INTO complaint VALUES
  ('complaint_1',NULL,'order_1','family_1','RESOLVED','Late arrival',
   'admin_1','Resolved',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO nurse_profile VALUES ('nurse_1');
INSERT INTO nurse_score VALUES ('nurse_1',100.00,0,0.00,0,NULL,'admin_1');
INSERT INTO metric_score_rule VALUES ('rule_complaint',NULL,'COMPLAINT',-5.00,1);
INSERT INTO order_metric_item VALUES
  ('metric_1','order_1',10.00,'EXEMPT_REJECTED',CURRENT_TIMESTAMP);
INSERT INTO metric_exception_proof VALUES
  ('proof_1','metric_1','nurse_1','REJECTED',CURRENT_TIMESTAMP);
INSERT INTO nurse_appeal VALUES
  ('appeal_1','nurse_1','METRIC','metric_1','Valid reason','[]','APPROVED',
   10.00,'Approved','admin_1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO training_article VALUES
  ('article_seed','Seed article','Summary','/seed','service_1',0,'OFFLINE',
   'admin_1',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO risk_tag VALUES ('risk_1','elder_1','FALL_RISK');
INSERT INTO care_service_evidence VALUES ('evidence_1','APPROVED',CURRENT_TIMESTAMP);
INSERT INTO bug_list VALUES ('bug_1','LOW','CLOSED');
