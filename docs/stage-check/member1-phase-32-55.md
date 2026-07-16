# Member 1 Phase 32-55 Database And Data Standards Check

Date: 2026-07-15

Scope: member-1 database schema, migration, permission seed, demo data, dashboard aggregate SQL, data dictionary, Redis policy, and database verification. Backend controllers/services, frontend pages, MinIO object creation, and Redis client implementation are outside this delivery.

## Delivered Files

| file | purpose |
| --- | --- |
| `db/schema/phase-32-55-complete-schema.sql` | Adds phase 32-55 database objects for reminders, metrics, evidence, AI audit logs, tickets, reviews, complaints, appeals, scoring, training articles, follow-up, and bug tracking. |
| `db/migration/phase-32-55-complete-contract.sql` | Existing-database migration entry that sources the idempotent phase 32-55 schema in Docker. |
| `db/seed/phase-32-55-demo-data.sql` | Adds frozen permission codes and demo data covering phases 32-55. |
| `db/statistics/phase-52-53-dashboard-statistics.sql` | Read-only basic and quality dashboard aggregate SQL over real business tables. |
| `docs/dictionary/phase-32-55-complete-data-dictionary.md` | Field/status dictionary, permission code freeze, database rules, and Redis cache policy. |
| `docs/test/member1-phase-32-55-db-check.sql` | SQL verification checklist for stage acceptance. |
| `docs/stage-check/member1-phase-32-55.md` | Member-1 verification and boundary record. |

## Requirement Coverage

| phase | member-1 database delivery |
| --- | --- |
| 32-33 | `reminder_task`, `reminder_record`, execution results for `DONE/SNOOZED/MISSED/NEED_HELP`. |
| 34-35 | `care_metric_config`, `care_metric_item`, `metric_score_rule`, `order_metric_checklist`, `order_metric_item`. |
| 36-40 | `care_service_evidence`, `evidence_review_record`, `nurse_metric_record`, `metric_exception_proof`, operation trace seed. |
| 41-42 | `ai_assistant_session`, `ai_assistant_message`, and `voice_command_log` trace/safety fields. |
| 43-44 | `assistance_ticket`, `customer_service_ticket`, `ticket_message`, `follow_up_record`. |
| 45-46 | `review`, `complaint`, `nurse_appeal`. |
| 47-48 | `nurse_score_change_log` and score-rule seed tied to existing `nurse_score`. |
| 49-50 | `training_article`, `article_tag`, `article_recommend_rule`, `nurse_article_reading`. |
| 51 | Follow-up record linked to a generated `reminder_task`. |
| 52-53 | Dashboard aggregate SQL uses real source tables, not fake summary tables. |
| 54-55 | Full-flow seed data and `bug_list` acceptance records. |

## Verification Commands

```powershell
docker cp db/schema/phase-32-55-complete-schema.sql carenest-mysql:/tmp/phase-32-55-complete-schema.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-32-55-complete-schema.sql"

docker cp db/seed/phase-32-55-demo-data.sql carenest-mysql:/tmp/phase-32-55-demo-data.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/phase-32-55-demo-data.sql"

docker cp docs/test/member1-phase-32-55-db-check.sql carenest-mysql:/tmp/member1-phase-32-55-db-check.sql
docker exec carenest-mysql mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing -e "source /tmp/member1-phase-32-55-db-check.sql"
```

## Verification Result

Executed on local Docker MySQL container `carenest-mysql` on 2026-07-15.

| check | result |
| --- | --- |
| Docker MySQL | healthy |
| phase 32-55 schema | passed |
| phase 32-55 schema rerun | passed |
| phase 32-55 migration entry | passed |
| phase 32-55 seed | passed |
| phase 32-55 seed rerun | passed |
| phase 52-53 dashboard SQL | passed |
| phase 32-55 verification SQL | passed |
| new phase 32-55 tables | `26` tables present |
| frozen phase 32-55 permissions | `13` permission codes present |
| role-permission seed | present for elder, family, admin, customer service by documented scope |
| `voice_command_log` extension | `session_id`, `trace_id`, `safety_flag`, `risk_level` present |
| reminder status coverage | `DONE=1`, `SNOOZED=1`, `MISSED=1`, `NEED_HELP=1` |
| order metric status coverage | `PASS=1`, `PENDING_PROOF=1`, `SUBMITTED=1` |
| evidence audit coverage | `APPROVED=1`, `PENDING=1` |
| AI session risk demo | `WARNING=1` |
| customer-service ticket demo | `RESOLVED=1` |
| review and complaint demo | `review=1`, `complaint=1` |
| nurse appeal demo | `APPROVED=1` |
| score change log demo | `METRIC=1`, `APPEAL=1` |
| training article and reading demo | `PUBLISHED=1`, `CONFIRMED=1` |
| follow-up demo | `1` follow-up linked to reminder task |
| bug list demo | `CLOSED=1`, `DEFERRED=1` |
| phase 52 source counts | `orders=4`, `reminderRecords=4`, `tickets=1`, `reviews=1` |
| phase 53 source counts | `metricItems=3`, `evidence=2`, `proofs=1`, `scoreChanges=2` |
| invalid reminder status insert | rejected by `ck_reminder_task_status`, no residue row |
| invalid metric status insert | rejected by `ck_order_metric_item_status`, no residue row |
| `git diff --check` | passed, with Windows CRLF normalization warnings only |
| `git diff --cached --check` | passed, with Windows CRLF normalization warnings only |
| `mvn -pl backend-care-admin test` | passed: 41 tests, 0 failures, 0 errors |
| `mvn test` | passed: `backend-user` 68 tests and `backend-care-admin` 41 tests |

Implementation note: the schema is idempotent for existing Docker databases and avoids triggers or stored procedures that would require elevated MySQL privileges. Cross-resource authorization remains a backend transaction/service rule; the database freezes the foreign keys, status constraints, indexes, and audit tables needed by that implementation.
