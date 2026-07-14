# Phase 19-25 Health Data Dictionary

This file freezes the database-side names, status values, and Redis key policy for phases 19-25. It is an extension of `docs/dictionary/data-dictionary.md` and avoids changing existing phase 01-18 definitions.

## Tables

| phase | table | purpose | owner |
| --- | --- | --- | --- |
| 19 | `health_archive` | One current archived health profile per elder, with `archive_version` for optimistic concurrency. | member1 |
| 19 | `chronic_disease` | Chronic disease entries under an elder health archive. | member1 |
| 19 | `medication_plan` | Medication records only; no diagnosis or medication recommendation. | member1 |
| 19 | `allergy_record` | Allergy records for pre-service safety checks. | member1 |
| 19 | `risk_tag` | Risk tags such as fall risk. | member1 |
| 19 | `care_plan` | Care plan text items. | member1 |
| 20 | `file_asset` | MinIO object metadata; never stores access key, secret key, or raw file content. | member1 |
| 20 | `medical_file` | Medical file metadata linked to elder and file asset. | member1 |
| 22 | `elder_health_feedback` | Elder health feedback submitted by button/text/voice. | member1 |
| 22 | `voice_command_log` | Voice command audit log linked to uploaded file metadata. | member1 |
| 23 | `health_update_suggestion` | Suggestions from nursing/report sources; does not directly update archive. | member1 |
| 24 | `health_info_review_task` extension | Adds `suggestion_id`, `task_type`, `NEED_MORE`, `MEDICAL_FILE`, and `SUGGESTION` compatibility. | member1 |

## Core Fields

| object | apiField | dbColumn | type | required | remark |
| --- | --- | --- | --- | --- | --- |
| HealthArchive | `archiveVersion` | `archive_version` | int | yes | Incremented when archive content is formally changed. |
| HealthArchive | `careSummary` | `care_summary` | varchar | no | Human-readable care summary. |
| RiskTag | `tagCode` | `tag_code` | varchar | yes | Stable frontend code such as `FALL_RISK`; unique per elder. |
| CarePlan | `careGoals` | `plan_content` JSON | varchar | yes | Goal text, maximum 300 characters. |
| CarePlan | `dailyCare` | `plan_content` JSON | varchar | yes | Daily care text, maximum 500 characters. |
| CarePlan | `precautions` | `plan_content` JSON | varchar | yes | Precaution text, maximum 500 characters. |
| ChronicDisease | `diseaseName` | `disease_name` | varchar | yes | Disease name as record text. |
| MedicationPlan | `timePoints` | `time_points` | JSON | no | Array of medication time strings, such as `["08:00"]`. |
| AllergyRecord | `allergen` | `allergen` | varchar | yes | Allergy source. |
| RiskTag | `riskLevel` | `risk_level` | varchar | no | Uses `LOW/MEDIUM/HIGH`. |
| FileAsset | `objectKey` | `object_key` | varchar | yes | Internal object key, backend-only. Frontend must not display it. |
| MedicalFile | `auditStatus` | `audit_status` | varchar | yes | Uses `PENDING/APPROVED/REJECTED/NEED_MORE`. |
| HealthFeedback | `inputType` | `input_type` | varchar | yes | Uses `BUTTON/TEXT/VOICE`. |
| HealthFeedback | `feedbackType` | `feedback_type` | varchar | yes | Uses `PAIN/DIZZINESS/SLEEP/DIET/MENTAL_STATE`. |
| HealthFeedback | `severity` | `severity` | varchar | yes | Uses `LOW/MEDIUM/HIGH`; controls list priority only. |
| HealthFeedback | `fileId` | `file_id` | varchar | voice only | Must reference an audio asset uploaded by the submitting elder. |
| HealthUpdateSuggestion | `suggestionStatus` | `suggestion_status` | varchar | yes | Uses `PENDING/APPROVED/REJECTED/NEED_MORE`. |
| HealthUpdateSuggestion | `sourceType` | `source_type` | varchar | yes | Uses `SERVICE_RECORD/SERVICE_REPORT`; source must belong to the order. |
| HealthUpdateSuggestion | - | `pending_dedupe_key` | generated char(64) | pending only | Prevents concurrent duplicate pending suggestions. |
| HealthInfoReviewTask | `taskType` | `task_type` | varchar | yes | Default `HEALTH_UPDATE`. |

## Status Values

| dictCode | value | meaning | allowed tables |
| --- | --- | --- | --- |
| auditStatus | `PENDING` | Uploaded and waiting for review. | `file_asset`, `medical_file` |
| auditStatus | `APPROVED` | Approved and visible to approved downstream reads. | `file_asset`, `medical_file` |
| auditStatus | `REJECTED` | Rejected by admin/customer service. | `file_asset`, `medical_file` |
| auditStatus | `NEED_MORE` | Requires supplementary material. | `file_asset`, `medical_file` |

The persisted/wire value for a newly uploaded medical file is `PENDING`. The Stage 20 frontend may normalize it to the UI-only alias `PENDING_REVIEW`; that alias must not be written to MySQL.
| healthReviewStatus | `PENDING` | Waiting for health archive review. | `health_info_review_task`, `health_update_suggestion` |
| healthReviewStatus | `APPROVED` | Approved and can be archived. | `health_info_review_task`, `health_update_suggestion` |
| healthReviewStatus | `REJECTED` | Rejected and must not change formal archive. | `health_info_review_task`, `health_update_suggestion` |
| healthReviewStatus | `NEED_MORE` | Needs more information and must not change formal archive. | `health_info_review_task`, `health_update_suggestion` |
| riskLevel | `LOW` | Low risk. | `risk_tag`, frontend display dictionary |
| riskLevel | `MEDIUM` | Medium risk. | `risk_tag`, frontend display dictionary |
| riskLevel | `HIGH` | High risk. | `risk_tag`, frontend display dictionary |
| recordStatus | `ACTIVE` | Current active entry. | `chronic_disease`, `medication_plan`, `care_plan` |
| recordStatus | `INACTIVE` | Historical or disabled entry. | `chronic_disease`, `medication_plan`, `care_plan` |

## Redis Key Policy For Implementers

Redis is not the source of truth. MySQL remains authoritative for orders, reports, bindings, health archive, files, and review tasks.

| owner | key | ttl | invalidation | sensitivity |
| --- | --- | --- | --- | --- |
| member2/member3 | `carenest:service-items:{scope}:v1` | 60s | service item create/update/delete | no personal data |
| member2 | `carenest:home:ELDER:{userHash}:v1` | 30s | report/order/feedback/archive changes | hashed user only |
| member2 | `carenest:home:FAMILY:{userHash}:v1` | 30s | binding/order/report/archive changes | hashed user only |
| member3 | `carenest:home:NURSE:{userHash}:v1` | 30s | dispatch/task/service record changes | hashed user only |
| member3 | `carenest:home:ADMIN:v1` | 30s | order/report/review task changes | aggregate only |
| member3 | `carenest:lock:order:{orderId}` | 10-30s | lock expires or unlocks after transaction | no value logging |
| member3 | `carenest:lock:report:{reportId}` | 10-30s | lock expires or unlocks after transaction | no value logging |
| member3 | `carenest:lock:archive:{taskId}` | 10-30s | lock expires or unlocks after transaction | no value logging |

Implementation rules:
- Always authorize user and resource scope before reading Redis.
- Write MySQL in a transaction first, then invalidate Redis after success.
- Phase 19 archive PUT and medication POST invalidate the elder home key and every active bound family home key after commit; rollback performs no invalidation.
- Do not cache raw JWT, password, phone number, MinIO keys, full medical file body, or full medical text.
- Redis outage must not produce fake success; reads may fall back to MySQL, writes must still rely on database consistency.
