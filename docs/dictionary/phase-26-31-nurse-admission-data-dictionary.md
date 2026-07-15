# Phase 26-31 Nurse Admission, Recommendation, And Attention Data Dictionary

This file freezes the database-side names, status values, permission codes, and Redis key policy for phases 26-31. It is a member-1 database and data-standards supplement to `docs/dictionary/data-dictionary.md`.

## Scope

Member 1 delivers MySQL schema, migration path, demo seed data, field/status dictionary, and Redis data policy only. Backend controller/service behavior, frontend pages, MinIO object creation, and Redis client implementation are not claimed here.

## Tables

| phase | table | purpose |
| --- | --- | --- |
| 26 | `nurse_profile` | Nurse identity, qualification status, training summary, and order-taking summary. |
| 26-27 | `nurse_certificate` | Qualification application, certificate file, submitted skill codes, audit result, and reviewer trace. |
| 28 | `nurse_training_record` | Training batch, status, pass time, expiry time, reviewer, and remark history. |
| 29 | `nurse_service_skill` | Nurse skill tags mapped to service items for recommendation filtering. |
| 29 | `nurse_score` | Score source used by recommendation sorting. |
| 29-30 | `nurse_recommendation_log` | Persisted recommendation candidates, scores, reasons, availability, and request context. |
| 30 | `nursing_order` extension | Adds preferred nurse, recommendation reason snapshot, selected time, and related log ID. |
| 31 | `care_attention_notice` | Pre-service risk/attention notice snapshot with idempotency hash. |
| 31 | `care_attention_ack` | Nurse acknowledgement record for visible attention notices. |

## Core Fields

| object | apiField | dbColumn | type | required | remark |
| --- | --- | --- | --- | --- | --- |
| NurseProfile | `nurseId` | `nurse_id` | varchar(32) | yes | Same value as `sys_user.user_id`; role must be `NURSE`. |
| NurseProfile | `realName` | `real_name` | varchar(64) | no | Snapshot source for qualification display. |
| NurseProfile | `idNoMasked` | `id_no_masked` | varchar(32) | no | Masked ID only; no full identity number is stored. |
| NurseProfile | `qualificationStatus` | `qualification_status` | varchar(32) | yes | Uses `PENDING/APPROVED/REJECTED/NEED_MORE`. |
| NurseProfile | `trainingStatus` | `training_status` | varchar(32) | yes | Stored summary only; `EXPIRED` is computed on read, not stored. |
| NurseCertificate | `applicationId` | `application_id` | varchar(32) | yes | Multiple file rows may share one application ID. |
| NurseCertificate | `certificateFileIds` | `file_id` | varchar(32) | yes | Must reference `file_asset` uploaded by the submitting nurse. |
| NurseCertificate | `serviceSkillCodes` | `service_skill_codes` | JSON | yes | Frozen code array, not free text. |
| NurseCertificate | `auditStatus` | `audit_status` | varchar(32) | yes | Qualification application audit status. |
| TrainingRecord | `trainingBatch` | `training_batch` | varchar(64) | yes | Batch name/code from admin workflow. |
| TrainingRecord | `expiredAt` | `expired_at` | datetime | approved only | Approved training must have an expiry time. |
| TrainingRecord | `displayStatus` | - | computed | yes | `EXPIRED` when latest approved record has `expired_at <= now`. |
| NurseServiceSkill | `skillCode` | `skill_code` | varchar(64) | yes | Example: `BASIC_CARE`, `VITAL_SIGN`, `REHAB_ASSIST`. |
| NurseScore | `score` | `total_score` | decimal(5,2) | yes | Score source range is `0-100`. |
| NurseRecommendation | `recommendReason` | `recommend_reason` | varchar(500) | yes | Human-readable reason persisted in MySQL. |
| NurseRecommendation | `available` | `available` | tinyint(1) | yes | `0` candidates cannot be selected as preference. |
| PreferredNurse | `preferredNurseId` | `preferred_nurse_id` | varchar(32) | no | Preference only; not dispatch result. |
| PreferredNurse | `recommendReason` | `preferred_nurse_reason` | varchar(500) | no | Snapshot filled from recommendation log when available. |
| AttentionNotice | `noticeId` | `notice_id` | varchar(32) | yes | Internal ID; frontend must not display it. |
| AttentionNotice | `level` | `notice_level` | varchar(32) | yes | Uses `INFO/WARNING/CRITICAL`. |
| AttentionNotice | `source` | `source_type` | varchar(32) | yes | Uses `HEALTH_ARCHIVE/MEDICAL_FILE/SERVICE_ITEM/ORDER_CONTEXT`. |
| AttentionNotice | `requiredAck` | `required_ack` | tinyint(1) | yes | Required items must be acknowledged before service start. |
| AttentionNotice | - | `notice_hash` | char(64) | yes | Idempotency hash for order, source, and normalized content. |
| AttentionAck | `ackedAt` | `acked_at` | datetime | yes | Acknowledgement time. |

## Status Values

| dictCode | value | meaning | persistence rule |
| --- | --- | --- | --- |
| auditStatus | `PENDING` | Waiting for review. | Stored in qualification and training tables. |
| auditStatus | `APPROVED` | Approved by authorized reviewer. | Stored in qualification and training tables. |
| auditStatus | `REJECTED` | Rejected with reason. | Stored in qualification and training tables. |
| auditStatus | `NEED_MORE` | Needs supplementary materials. | Stored in qualification and training tables. |
| trainingDisplayStatus | `EXPIRED` | Training has expired. | Computed only from `APPROVED + expired_at <= now`; never stored. |
| noticeLevel | `INFO` | Normal care reminder. | Stored in `care_attention_notice.notice_level`. |
| noticeLevel | `WARNING` | Important care reminder. | Stored in `care_attention_notice.notice_level`. |
| noticeLevel | `CRITICAL` | High-risk care reminder. | Stored in `care_attention_notice.notice_level`. |
| noticeSource | `HEALTH_ARCHIVE` | Health archive or approved archive item. | Stored as source type only; do not copy full medical text. |
| noticeSource | `MEDICAL_FILE` | Approved medical file summary. | Store minimum necessary summary only. |
| noticeSource | `SERVICE_ITEM` | Service item attention rule. | Store service rule snapshot. |
| noticeSource | `ORDER_CONTEXT` | Current order context. | Store order-specific reminder. |

## Permission Codes

| permissionCode | permissionName | default role seed |
| --- | --- | --- |
| `NURSE_QUALIFICATION_SUBMIT` | Submit nurse qualification | `NURSE` |
| `NURSE_QUALIFICATION_REVIEW` | Review nurse qualification | `ADMIN`, `CUSTOMER_SERVICE` |
| `NURSE_TRAINING_REVIEW` | Review nurse training | `ADMIN`, `CUSTOMER_SERVICE` |
| `NURSE_RECOMMEND_VIEW` | View nurse recommendation | `FAMILY`, `ADMIN`, `NURSE` |
| `NURSE_PREFERENCE_SELECT` | Select preferred nurse | `FAMILY` |
| `NURSE_ATTENTION_ACK` | Acknowledge pre-service attention notices | `NURSE` |
| `CARE_ATTENTION_REVIEW` | Review pre-service attention notices | `ADMIN`, `CUSTOMER_SERVICE` |

Permission code checks must be combined with resource checks: family order ownership and active binding scope, nurse self/assigned-order scope, and admin/customer-service authorization.

## Database Rules

- `nurse_certificate.file_id` references `file_asset.file_id`; the composite foreign key `(file_id, nurse_id)` to `(file_id, uploaded_by)` rejects certificate files not uploaded by the submitting nurse.
- A nurse may have at most one active `PENDING` or `APPROVED` qualification application. Because the current backend stores one row per certificate file, this cross-row application uniqueness is enforced by backend transaction logic and supported by `idx_nurse_certificate_current`.
- Rejected or need-more applications remain historical records; resubmission must create a new application.
- `nurse_training_record` must only be written for nurses whose qualification summary is `APPROVED`; this remains a backend transaction rule because it depends on current `nurse_profile` state.
- Approved training records require `expired_at`, enforced by CHECK constraint. `EXPIRED` remains a read-only computed status.
- `nurse_recommendation_log` is always persisted, even when Redis is used.
- `nursing_order.preferred_nurse_id` is only a family preference and must not create `nurse_task` or change `order_status`.
- `care_attention_notice` uses `notice_hash` to prevent repeated reads from generating duplicate notices for the same order/source/content.
- `care_attention_ack` is unique per `notice_id + nurse_id`, enabling idempotent acknowledgement.
- Sensitive values such as full identity numbers, raw medical file text, MinIO secrets, passwords, and JWTs must not be stored in these tables.

## Redis Key Policy

Redis is a cache only. MySQL remains authoritative for qualifications, training records, skills, scores, orders, recommendation logs, and attention acknowledgements.

| owner | key | ttl | source of truth | invalidation |
| --- | --- | --- | --- | --- |
| backend recommendation | `recommend:nurses:{requestHash}` | <= 5 minutes | `nurse_recommendation_log` plus current nurse/order tables | Qualification review, training review/expiry, skill change, score change, schedule change, order service/time/address change. |

Rules:

- Cache values may contain candidate IDs, score, matched skill codes, availability, and reason, but cannot be the only persisted record.
- On preference selection, backend must recheck candidate qualification, training validity, availability, order status, and current recommendation scope.
- Redis outage must fall back to MySQL reads or fail the cache layer transparently; it must not produce fake success.
- Do not cache full certificate numbers, identity numbers, medical text, object storage credentials, password hashes, or JWTs.
