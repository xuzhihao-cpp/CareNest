USE smart_nursing;
SET NAMES utf8mb4;

SELECT 'phase32_reminder_task' AS check_item, COUNT(*) AS actual_count FROM reminder_task;
SELECT 'phase33_reminder_record' AS check_item, result, COUNT(*) AS actual_count FROM reminder_record GROUP BY result;
SELECT 'phase34_metric_config' AS check_item, COUNT(*) AS actual_count FROM care_metric_config;
SELECT 'phase35_order_metric_item' AS check_item, metric_status, COUNT(*) AS actual_count FROM order_metric_item GROUP BY metric_status;
SELECT 'phase36_evidence' AS check_item, audit_status, COUNT(*) AS actual_count FROM care_service_evidence GROUP BY audit_status;
SELECT 'phase37_evidence_review' AS check_item, COUNT(*) AS actual_count FROM evidence_review_record;
SELECT 'phase38_nurse_metric_record' AS check_item, metric_status, COUNT(*) AS actual_count FROM nurse_metric_record GROUP BY metric_status;
SELECT 'phase39_exception_proof' AS check_item, proof_status, COUNT(*) AS actual_count FROM metric_exception_proof GROUP BY proof_status;
SELECT 'phase42_ai_session' AS check_item, safety_level, COUNT(*) AS actual_count FROM ai_assistant_session GROUP BY safety_level;
SELECT 'phase43_ticket' AS check_item, ticket_status, COUNT(*) AS actual_count FROM customer_service_ticket GROUP BY ticket_status;
SELECT 'phase41_43_ai_ticket_link' AS check_item,
  (SELECT COUNT(*) FROM ai_assistant_session WHERE session_id = 'ai_session_041') AS session_count,
  (SELECT COUNT(*) FROM ai_assistant_message WHERE session_id = 'ai_session_041') AS message_count,
  (SELECT COUNT(*) FROM assistance_ticket WHERE session_id = 'ai_session_041') AS assistance_count,
  (SELECT COUNT(*) FROM customer_service_ticket WHERE assistance_ticket_id = 'assist_043_001') AS cs_ticket_count;
SELECT 'phase45_review_complaint' AS check_item, (SELECT COUNT(*) FROM review) AS review_count, (SELECT COUNT(*) FROM complaint) AS complaint_count;
SELECT 'phase46_appeal' AS check_item, appeal_status, COUNT(*) AS actual_count FROM nurse_appeal GROUP BY appeal_status;
SELECT 'phase47_score_log' AS check_item, source_event_type, COUNT(*) AS actual_count FROM nurse_score_change_log GROUP BY source_event_type;
SELECT 'phase49_article' AS check_item, article_status, COUNT(*) AS actual_count FROM training_article GROUP BY article_status;
SELECT 'phase50_reading' AS check_item, reading_status, COUNT(*) AS actual_count FROM nurse_article_reading GROUP BY reading_status;
SELECT 'phase51_follow_up' AS check_item, COUNT(*) AS actual_count FROM follow_up_record;
SELECT 'phase55_bug_list' AS check_item, bug_status, COUNT(*) AS actual_count FROM bug_list GROUP BY bug_status;

SELECT
  'phase52_basic_dashboard_sources' AS check_item,
  (SELECT COUNT(*) FROM nursing_order) AS orders_count,
  (SELECT COUNT(*) FROM reminder_record) AS reminder_record_count,
  (SELECT COUNT(*) FROM customer_service_ticket) AS ticket_count,
  (SELECT COUNT(*) FROM review) AS review_count;

SELECT
  'phase53_quality_dashboard_sources' AS check_item,
  (SELECT COUNT(*) FROM order_metric_item) AS metric_item_count,
  (SELECT COUNT(*) FROM care_service_evidence) AS evidence_count,
  (SELECT COUNT(*) FROM metric_exception_proof) AS proof_count,
  (SELECT COUNT(*) FROM nurse_score_change_log) AS score_change_count;
