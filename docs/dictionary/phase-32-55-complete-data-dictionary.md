# Phase 32-55 Complete Database And Data Standards

This file freezes the member-1 database-side table names, field names, status values, permissions, Redis policy, and dashboard query sources for phases 32-55. It supplements `docs/dictionary/data-dictionary.md`.

## Scope

Member 1 delivers MySQL schema, migration path, demo seed data, dashboard aggregate SQL, data dictionary, Redis data policy, and database verification only. Backend controller/service implementation, frontend pages, MinIO object creation, and Redis client implementation are not claimed here.

## Tables

| phase | table | purpose |
| --- | --- | --- |
| 32-33 | `reminder_task`, `reminder_record` | Elder reminder tasks and execution records. |
| 34 | `care_metric_config`, `care_metric_item`, `metric_score_rule` | Service care metric configuration and score rules. |
| 35 | `order_metric_checklist`, `order_metric_item` | Order-level checklist generated from active service metrics. |
| 36-37 | `care_service_evidence`, `evidence_review_record` | Nurse evidence upload metadata and review history. |
| 38-40 | `nurse_metric_record`, `metric_exception_proof`, `operation_log` | Metric execution source records, exception proof, and operation trace. |
| 41-42 | `ai_assistant_session`, `ai_assistant_message`, `voice_command_log` extension | AI session/message audit trail and voice command trace fields. |
| 43-44 | `assistance_ticket`, `customer_service_ticket`, `ticket_message`, `follow_up_record` | Assistance and customer-service ticket workflow. |
| 45-46 | `review`, `complaint`, `nurse_appeal` | User review, complaint, and nurse appeal records. |
| 47-48 | `nurse_score`, `nurse_score_change_log`, `metric_score_rule` | Nurse score source and auditable score changes. |
| 49-50 | `training_article`, `article_tag`, `article_recommend_rule`, `nurse_article_reading` | Nurse training article publishing, tags, recommendations, and reading records. |
| 51 | `follow_up_record`, `reminder_task` | Follow-up record and next-reminder linkage. |
| 52-53 | `db/statistics/phase-52-53-dashboard-statistics.sql` | Basic and quality dashboard aggregate SQL over real tables. |
| 54-55 | `db/seed/phase-32-55-demo-data.sql`, `bug_list` | Full-flow demo data and acceptance bug list. |

## Status Values

| dictCode | values | database columns |
| --- | --- | --- |
| `reminderStatus` | `PENDING`, `DONE`, `SNOOZED`, `NEED_HELP`, `MISSED` | `reminder_task.reminder_status`, `reminder_record.result` |
| `metricStatus` | `PENDING`, `SUBMITTED`, `PASS`, `MISSING`, `PENDING_PROOF`, `EXEMPT_APPROVED`, `EXEMPT_REJECTED` | `order_metric_item.metric_status`, `nurse_metric_record.metric_status` |
| `evidenceAuditStatus` | `PENDING`, `APPROVED`, `REJECTED`, `NEED_MORE` | `care_service_evidence.audit_status`, `evidence_review_record.to_status` |
| `proofStatus` | `PENDING`, `APPROVED`, `REJECTED` | `metric_exception_proof.proof_status` |
| `aiSessionStatus` | `ACTIVE`, `CLOSED` | `ai_assistant_session.session_status` |
| `aiSafetyLevel` | `NORMAL`, `WARNING`, `CRITICAL` | `ai_assistant_session.safety_level`, `voice_command_log.risk_level` |
| `ticketStatus` | `PENDING`, `PROCESSING`, `RESOLVED`, `CLOSED` | `assistance_ticket.ticket_status`, `customer_service_ticket.ticket_status` |
| `complaintStatus` | `PENDING`, `PROCESSING`, `RESOLVED`, `REJECTED` | `complaint.complaint_status` |
| `appealStatus` | `PENDING`, `APPROVED`, `REJECTED` | `nurse_appeal.appeal_status` |
| `articleStatus` | `DRAFT`, `PUBLISHED`, `OFFLINE` | `training_article.article_status` |
| `readingStatus` | `UNREAD`, `READ`, `CONFIRMED` | `nurse_article_reading.reading_status` |
| `bugStatus` | `OPEN`, `PROCESSING`, `CLOSED`, `DEFERRED` | `bug_list.bug_status` |

## Permission Codes

| permissionCode | default role seed |
| --- | --- |
| `REMINDER_VIEW`, `REMINDER_UPDATE`, `REMINDER_RECORD_VIEW` | elder, family, admin, customer service by resource scope |
| `CARE_METRIC_CONFIG_MANAGE`, `CARE_EVIDENCE_REVIEW` | admin, customer service |
| `AI_SESSION_REVIEW` | admin, customer service |
| `COMPLAINT_HANDLE`, `NURSE_APPEAL_REVIEW` | customer service, admin |
| `TRAINING_ARTICLE_MANAGE`, `FOLLOW_UP_MANAGE` | admin, customer service |
| `DASHBOARD_BASIC_VIEW`, `DASHBOARD_QUALITY_VIEW`, `DEMO_DATA_MANAGE` | admin, customer service |

Permission checks must be combined with resource ownership checks: elder self scope, active family binding and authorization scope, nurse assigned-order scope, and admin/customer-service authorization.

## Database Rules

- MySQL is the authoritative source for reminders, metrics, evidence, AI audit logs, tickets, reviews, complaints, appeals, scores, training articles, follow-ups, and final demo data.
- `reminder_record` is append-only for execution history. Updating `reminder_task.reminder_status` must be paired with a record row in the same backend transaction.
- `order_metric_checklist` is unique per order. Generated checklist items keep metric code, name, evidence type, and score weight snapshots so later config changes do not rewrite historical orders.
- `care_service_evidence` stores evidence metadata only. Object storage bucket/key remains in `file_asset`; the database does not store file bytes or MinIO credentials.
- Exception proof approval must write both `metric_exception_proof` and a corresponding `nurse_metric_record`/`nurse_score_change_log` when it affects score.
- AI messages store audit summaries and trace IDs. They must not store raw secrets, passwords, JWTs, or long sensitive medical text beyond the minimum audit summary needed by the workflow.
- Dashboard phases 52-53 must aggregate from real business tables. No fake dashboard tables are introduced.
- Phase 55 `bug_list` is an acceptance tracking table only; it is not used to mask failing business checks.

## Redis Key Policy

Redis is a cache and coordination layer only. It must not replace MySQL persistence.

| owner | key | ttl | source of truth | invalidation |
| --- | --- | --- | --- | --- |
| reminder center | `carenest:reminders:{elderId}:v1` | <= 60 seconds | `reminder_task`, `reminder_record` | Reminder create/update/complete/snooze/need-help/missed. |
| basic dashboard | `carenest:dashboard:basic:{rangeHash}:v1` | <= 30 seconds | `nursing_order`, `reminder_record`, `customer_service_ticket`, `review` | Order, reminder, ticket, review writes. |
| quality dashboard | `carenest:dashboard:quality:{rangeHash}:v1` | <= 30 seconds | `order_metric_item`, `care_service_evidence`, `metric_exception_proof`, `nurse_score`, `nurse_score_change_log` | Metric, evidence, proof, score writes. |
| article recommendation | `carenest:training:articles:{nurseId}:{contextHash}:v1` | <= 10 minutes | `training_article`, `article_tag`, `article_recommend_rule`, `nurse_article_reading` | Article publish/offline, tag/rule update, reading confirmation. |

Rules:

- Cache values may contain IDs, status summaries, counts, and display-safe summaries only.
- Redis must not contain password hashes, JWTs, full identity numbers, raw medical files, MinIO credentials, or long AI conversation content.
- Redis outage must fall back to MySQL for reads or degrade only the cache path. It must not produce fake success or skip database writes.
- Backend writes must commit MySQL first, then evict or refresh cache keys.
