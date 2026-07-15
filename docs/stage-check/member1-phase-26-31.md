# Member 1 Phase 26-31 Database And Data Standards Check

Date: 2026-07-15

Scope: database schema, migration, permission seed, demo data, data dictionary, Redis data policy, and member-1 verification only. No backend controller/service implementation, frontend page implementation, MinIO object creation, or Redis client implementation is claimed in this delivery.

## Delivered Files

| file | purpose |
| --- | --- |
| `db/schema/phase-26-31-nurse-admission-schema.sql` | Adds nurse admission, qualification, training, skills, scores, recommendation log, order preference fields, and pre-service attention tables. |
| `db/migration/phase-26-31-nurse-admission-contract.sql` | Existing-database migration entry that runs the idempotent phase 26-31 schema baseline in Docker. |
| `db/seed/phase-26-31-demo-data.sql` | Adds frozen permission codes and demo data for qualification, training, recommendation, preference, no-candidate, and attention scenarios. |
| `docs/dictionary/phase-26-31-nurse-admission-data-dictionary.md` | Field dictionary, status dictionary, permission code freeze, database rules, and Redis cache policy. |
| `docs/stage-check/member1-phase-26-31.md` | Member-1 verification and boundary record. |

## Boundary Check

- MySQL remains the source of truth for qualification, training, recommendation logs, order preferences, and attention acknowledgements.
- Redis is documented only as a short-lived recommendation cache: `recommend:nurses:{requestHash}`, TTL no more than 5 minutes.
- `nurse_recommendation_log` is mandatory even when Redis hits.
- Full identity numbers, raw medical text, MinIO credentials, passwords, and JWTs are not stored.
- `nursing_order.preferred_nurse_id` is a preference only. It does not create `nurse_task` and does not change `order_status`.
- Phase 31 database tables are ready, but this member-1 work does not claim the phase 31 backend endpoints or start-service state-machine gate are implemented.

## Schema Rules

- `nurse_certificate.file_id` must reference a real `file_asset` row owned by the submitting nurse; this is enforced by a composite foreign key.
- A nurse cannot have more than one active `PENDING` or `APPROVED` qualification application; because current backend stores one row per certificate file, this cross-row rule is enforced by backend transaction logic and supported by a current-application index.
- Rejected and need-more qualification records remain historical and can be followed by a new application.
- Training records can only be inserted for nurses whose qualification summary is `APPROVED`; this is a backend transaction rule based on current nurse state.
- Approved training requires `expired_at`, enforced by CHECK constraint; `EXPIRED` is computed on read from `expired_at <= now` and is not stored as a database status.
- Pre-service attention notices use `(order_id, source_type, source_id, notice_hash)` as an idempotency key.
- Attention acknowledgements are unique per `notice_id + nurse_id`.

## Demo Data Coverage

| requirement | seed row |
| --- | --- |
| Nurse without application | `nurse-noapp-026` |
| Pending qualification nurse | `nurse-pending-026` / `app_pending_026` |
| Need-more qualification nurse | `nurse-needmore-026` / `app_needmore_026` |
| Qualification approved but training not approved | `nurse-qualified-026` / `training_rejected_026` |
| Training-valid nurse | `nurse-valid-028` / `training_valid_028` |
| Training-expired nurse | `nurse-expired-028` / `training_expired_028` |
| Two recommendable nurses with different scores/skills | `nurse-reco-a-029`, `nurse-reco-b-029` |
| No-candidate service scene | `service_029_none` / `order_029_no_candidate` |
| Preferred nurse trace | `order_029_001` with recommendation logs and preferred nurse snapshot |
| Pre-service attention notices | `order_031_001`, `task_031_001`, `notice_031_*` |

## Verification Commands

```powershell
docker cp db/schema/phase-26-31-nurse-admission-schema.sql carenest-mysql:/tmp/phase-26-31-nurse-admission-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-26-31-nurse-admission-schema.sql"

docker cp db/seed/phase-26-31-demo-data.sql carenest-mysql:/tmp/phase-26-31-demo-data.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-26-31-demo-data.sql"
```

Expected database checks:

```sql
SELECT COUNT(*) FROM nurse_profile WHERE nurse_id IN (
  'nurse-noapp-026','nurse-pending-026','nurse-needmore-026','nurse-qualified-026',
  'nurse-valid-028','nurse-expired-028','nurse-reco-a-029','nurse-reco-b-029'
);
SELECT audit_status, COUNT(*) FROM nurse_certificate GROUP BY audit_status;
SELECT training_status, COUNT(*) FROM nurse_training_record GROUP BY training_status;
SELECT order_id, preferred_nurse_id, preferred_nurse_reason FROM nursing_order WHERE order_id = 'order_029_001';
SELECT COUNT(*) FROM nurse_recommendation_log WHERE order_id = 'order_029_001';
SELECT COUNT(*) FROM nurse_service_skill WHERE service_id = 'service_029_none';
SELECT COUNT(*) FROM care_attention_notice WHERE order_id = 'order_031_001';
SELECT COUNT(*) FROM care_attention_ack WHERE order_id = 'order_031_001';
```

## Verification Result

Executed on local Docker MySQL container `carenest-mysql` on 2026-07-15.

Result summary:

| check | result |
| --- | --- |
| phase 26-31 schema | passed |
| phase 26-31 schema rerun | passed |
| phase 26-31 migration script copied into container and executed | passed |
| phase 26-31 seed | passed |
| phase 26-31 seed rerun | passed |
| frozen permissions | `7` |
| phase nurse profiles | `8` |
| qualification statuses | `APPROVED=5`, `NEED_MORE=1`, `PENDING=1` |
| training statuses | `APPROVED=4`, `REJECTED=1` |
| `order_029_001` preferred nurse | `nurse-reco-a-029` / `reclog_029_001_a` |
| recommendation logs for `order_029_001` | `2` |
| skill rows for no-candidate service `service_029_none` | `0` |
| attention notices for `order_031_001` | `3` |
| attention acknowledgements for `order_031_001` | `1` |
| composite certificate file-owner FK | present |
| preferred nurse FK and recommendation log FK | present |
| training CHECK constraints | present |
| invalid certificate file-owner insert | rejected by `fk_nurse_certificate_file_owner`, no residue rows |
| invalid approved training without `expired_at` | rejected by `ck_nurse_training_approved_expiry`, no residue rows |
| `git diff --check` | passed, with CRLF normalization warnings on existing docs |
| `git diff --check --cached` | passed |
| `mvn -pl backend-care-admin test` | passed: 41 tests, 0 failures, 0 errors |

Implementation note: the initial schema draft used triggers for several cross-table checks, but local MySQL rejected trigger creation for the project user because binary logging requires elevated trigger privileges. The final schema avoids that deployment risk by using composite foreign keys and CHECK constraints where MySQL can enforce the rule without `SUPER`; cross-row active-application uniqueness and qualification-before-training remain backend transaction rules documented in the dictionary.

Container note: the currently running local MySQL container did not expose `/opt/carenest/migration`, so the migration file was copied to `/tmp` for execution during this check. The migration content itself executed successfully and sourced the mounted schema file.
