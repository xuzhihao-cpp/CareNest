USE smart_nursing;
SET NAMES utf8mb4;

-- Phase 52 basic dashboard: read-only aggregates from real business tables.
-- These SQL statements are query contracts for backend implementation and
-- acceptance checks. They are not schema changes.

SELECT
  'orders_by_status' AS metric_name,
  order_status AS metric_key,
  COUNT(*) AS metric_value
FROM nursing_order
GROUP BY order_status
ORDER BY order_status;

SELECT
  'reminder_records_by_result' AS metric_name,
  result AS metric_key,
  COUNT(*) AS metric_value
FROM reminder_record
GROUP BY result
ORDER BY result;

SELECT
  'customer_service_tickets_by_status' AS metric_name,
  ticket_status AS metric_key,
  COUNT(*) AS metric_value
FROM customer_service_ticket
GROUP BY ticket_status
ORDER BY ticket_status;

SELECT
  'reviews_summary' AS metric_name,
  'all' AS metric_key,
  COUNT(*) AS review_count,
  ROUND(AVG(rating), 2) AS avg_rating,
  ROUND(AVG(satisfaction), 2) AS avg_satisfaction
FROM review;

-- Phase 53 quality dashboard: read-only aggregates from metric, evidence, and
-- nurse score source tables. MySQL remains the source of truth.

SELECT
  'order_metric_items_by_status' AS metric_name,
  metric_status AS metric_key,
  COUNT(*) AS metric_value
FROM order_metric_item
GROUP BY metric_status
ORDER BY metric_status;

SELECT
  'care_evidence_by_audit_status' AS metric_name,
  audit_status AS metric_key,
  COUNT(*) AS metric_value
FROM care_service_evidence
GROUP BY audit_status
ORDER BY audit_status;

SELECT
  'metric_exception_proofs_by_status' AS metric_name,
  proof_status AS metric_key,
  COUNT(*) AS metric_value
FROM metric_exception_proof
GROUP BY proof_status
ORDER BY proof_status;

SELECT
  'nurse_score_summary' AS metric_name,
  'all' AS metric_key,
  COUNT(*) AS nurse_count,
  ROUND(AVG(total_score), 2) AS avg_total_score,
  MIN(total_score) AS min_total_score,
  MAX(total_score) AS max_total_score,
  SUM(complaint_count) AS total_complaint_count
FROM nurse_score;

SELECT
  'nurse_score_change_by_event' AS metric_name,
  source_event_type AS metric_key,
  COUNT(*) AS change_count,
  SUM(score_delta) AS score_delta_sum
FROM nurse_score_change_log
GROUP BY source_event_type
ORDER BY source_event_type;
