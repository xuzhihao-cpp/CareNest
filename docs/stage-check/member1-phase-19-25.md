# Member 1 Phase 19-25 Database And Data Standards Check

Date: 2026-07-11

Scope: database schema, migration, demo data, dictionary, and Redis data policy only. No backend controller/service or frontend page implementation is claimed in this member-1 delivery.

## Delivered Files

| file | purpose |
| --- | --- |
| `db/schema/phase-19-health-archive-schema.sql` | Adds health archive, chronic disease, medication, allergy, risk tag, and care plan tables. |
| `db/schema/phase-20-medical-file-schema.sql` | Adds file metadata and medical file metadata tables for MinIO-backed uploads. |
| `db/schema/phase-22-health-feedback-schema.sql` | Adds elder health feedback and voice command audit tables. |
| `db/schema/phase-23-health-update-suggestion-schema.sql` | Adds health archive update suggestion table. |
| `db/schema/phase-24-health-review-task-extension.sql` | Extends `health_info_review_task` for phase 21/23/24 review flow. |
| `db/migration/phase-24-health-review-archive-extension.sql` | Repeatable migration for existing databases. |
| `db/seed/phase-19-25-demo-data.sql` | Demo data for elder health archive, medical files, feedback, and pending review task. |
| `docs/dictionary/phase-19-25-health-data-dictionary.md` | Field dictionary, status dictionary, and Redis key policy for implementers. |

## Boundary Check

- Kept MySQL as the source of truth for health archive, medical files, feedback, suggestions, and review tasks.
- Did not add backend API implementation, frontend implementation, Redis client code, or MinIO client code.
- Did not change existing controller/service signatures.
- Did not claim phase 19-25 functional completion for other members.
- `backend-user/src/test/resources/test-schema.sql` still has the pre-existing binding-scope test fields: `pending_scope_codes`, `scope_update_status`.

## Schema Rules

- Medical file audit status is frozen to `PENDING`, `APPROVED`, `REJECTED`, `NEED_MORE`.
- Health review status is frozen to `PENDING`, `APPROVED`, `REJECTED`, `NEED_MORE`.
- Suggestions do not directly update `health_archive`; formal archive update must go through `health_info_review_task`.
- `file_asset` stores object metadata only, not object storage credentials or file body.
- Redis policy is documented only; implementation remains with the relevant backend members.

## Verification To Run Before PR

```powershell
docker cp db/schema/phase-19-health-archive-schema.sql carenest-mysql:/tmp/phase-19-health-archive-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-19-health-archive-schema.sql"

docker cp db/schema/phase-20-medical-file-schema.sql carenest-mysql:/tmp/phase-20-medical-file-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-20-medical-file-schema.sql"

docker cp db/schema/phase-22-health-feedback-schema.sql carenest-mysql:/tmp/phase-22-health-feedback-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-22-health-feedback-schema.sql"

docker cp db/schema/phase-23-health-update-suggestion-schema.sql carenest-mysql:/tmp/phase-23-health-update-suggestion-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-23-health-update-suggestion-schema.sql"

docker cp db/migration/phase-24-health-review-archive-extension.sql carenest-mysql:/tmp/phase-24-health-review-archive-extension.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-24-health-review-archive-extension.sql"

docker cp db/seed/phase-19-25-demo-data.sql carenest-mysql:/tmp/phase-19-25-demo-data.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-19-25-demo-data.sql"
```

Expected database checks:

```sql
SELECT COUNT(*) FROM health_archive WHERE elder_id = 'elder_001';
SELECT COUNT(*) FROM medical_file WHERE elder_id = 'elder_001';
SELECT COUNT(*) FROM elder_health_feedback WHERE elder_id = 'elder_001';
SELECT COUNT(*) FROM health_update_suggestion WHERE elder_id = 'elder_001';
SELECT review_task_id, suggestion_id, task_type, review_status FROM health_info_review_task WHERE review_task_id = 'review_task_019_001';
```

Expected result: each count is at least `1`, and the review task has `task_type = 'HEALTH_UPDATE'` and `review_status = 'PENDING'`.

## Verification Result

Executed on local Docker MySQL container `carenest-mysql` on 2026-07-11.

Result summary:

| check | result |
| --- | --- |
| phase 19 schema | passed |
| phase 20 schema | passed |
| phase 22 schema | passed |
| phase 23 schema | passed |
| phase 24 schema extension | passed |
| phase 24 migration rerun | passed |
| phase 19-25 seed | passed |
| `health_archive` for `elder_001` | `1` |
| `medical_file` for `elder_001` | `2` |
| `elder_health_feedback` for `elder_001` | `1` |
| `health_update_suggestion` for `elder_001` | `1` |
| `review_task_019_001` | `HEALTH_UPDATE / PENDING` |
