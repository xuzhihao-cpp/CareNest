# Phase 23 Health Update Suggestions Design

## Scope

Phase 23 lets the nurse assigned to a real order propose one structured change to an elder health archive based on that order's saved service record or generated service report. It creates a pending suggestion and reuses `health_info_review_task`; it never writes `health_archive` or any Phase 19 child table. Phase 24 remains the only approval and archive boundary.

Fixed APIs:

- `POST /api/v1/orders/{orderId}/health-update-suggestions`
- `GET /api/v1/admin/health-review-tasks`

Both routes belong to `backend-care-admin` on port 8082, matching the current Nginx routing and ownership of orders, nurse tasks, service records, and reports.

## Authorization

Suggestion creation requires the `NURSE` role and a `nurse_task` for the same `orderId` whose `nurse_id` equals the authenticated user. Family, elder, admin, customer-service, anonymous, and nurses assigned to another order are rejected.

Review-task listing requires `ADMIN` or `CUSTOMER_SERVICE` plus an enabled permission matching one of the frozen frontend aliases: `HEALTH_REVIEW`, `HEALTH_ARCHIVE_REVIEW`, or `health:review`. The initial database migration provisions `HEALTH_ARCHIVE_REVIEW` and assigns it to the admin and customer-service roles. Authorization is checked through `user_role`, `role_permission`, and `sys_permission`, not through client-supplied values.

## Source Integrity

`sourceType` is limited to `SERVICE_RECORD` and `SERVICE_REPORT`.

- `SERVICE_RECORD`: `sourceId` must identify a `care_service_record` whose `order_id` is the path order.
- `SERVICE_REPORT`: `sourceId` must identify a `service_report` whose `order_id` is the path order.

The source summary returned to the admin is built from persisted source content. A source from another order, a nonexistent source, or a type/id mismatch returns `422`; no suggestion is written.

## Structured Fields

Allowed wire field names are exactly `diseases`, `medications`, `allergies`, `riskTags`, and `carePlan`. `newValue` is parsed as a typed JSON object and normalized before storage:

- `diseases`: disease name, allowed status, optional diagnosis date and remark.
- `medications`: medicine name, allowed frequency, valid dates/time points, optional dosage and remark.
- `allergies`: allergen name, allowed severity, optional reaction and remark.
- `riskTags`: stable tag code and display name.
- `carePlan`: nonblank care goals, daily care, and precautions.

Unknown properties, raw SQL fragments, arrays replacing an entire archive section, and oversized values are rejected with `422`. `reason` is trimmed, 5-255 characters. `old_value` is a JSON snapshot of the relevant current Phase 19 data at submission time; `new_value` is canonical JSON.

## Transaction And Idempotency

One database transaction:

1. Locks/checks the order and nurse assignment.
2. Validates the persisted source and structured value.
3. Checks for a pending suggestion with the same order, source type, source id, field, and canonical new value.
4. On duplicate, returns `409` and creates nothing.
5. Inserts `health_update_suggestion` with `PENDING`.
6. Inserts one `health_info_review_task` linked by `suggestion_id`, with `task_type=HEALTH_UPDATE` and `review_status=PENDING`.
7. Updates the suggestion's `review_task_id` and inserts `operation_log`.

Any failure rolls back all three writes. A database unique key on the idempotency dimensions protects concurrent duplicate requests.

## Admin Query

The admin endpoint supports `page`, `size`, `status`, `sourceType`, and `keyword`. It joins suggestions, review tasks, elder profiles, orders, and service items. It returns the frontend contract fields: task status, elder and service names, source type/summary, field, current/suggested values, reason, and submission time. Internal IDs are present only where required for later Phase 24 actions, not as display titles.

## Tests And Acceptance

- TDD integration tests cover valid service-record and report suggestions, archive version/content unchanged, admin visibility, role and permission denial, wrong nurse, forged source, invalid structured fields, and duplicate `409` without extra rows.
- Schema and migration tests verify the Phase 16 task table is reused and the suggestion/task/log references are traceable.
- OpenAPI and generated frontend types are updated.
- Docker acceptance uses real nurse/admin tokens and MySQL data through `http://localhost:3000`, then verifies the archive is unchanged and the admin queue contains the new task.
