# Phase 23 Health Update Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist nurse-originated, source-backed health archive suggestions into the existing review queue without modifying the formal archive.

**Architecture:** Add a focused `healthsuggestion` package to `backend-care-admin`. A controller exposes the two frozen APIs, a transactional service enforces authorization and typed values, and a JDBC repository owns source lookup, archive snapshots, idempotency, writes, and admin pagination. Existing frontend code consumes the generated contract unchanged.

**Tech Stack:** Java 17, Spring Boot 3, JdbcTemplate, MySQL 8, JUnit 5/MockMvc, Vue/TypeScript contract generation, Docker Compose.

## Global Constraints

- Reuse `health_info_review_task`; do not create a parallel review table.
- Never update `health_archive` or Phase 19 child tables during Phase 23.
- Duplicate pending suggestions return HTTP 409 and create no rows.
- Only the assigned nurse may create a suggestion from a source belonging to that order.
- Admin reads require role plus an enabled health-review permission.

---

### Task 1: Freeze API and transaction behavior

**Files:**
- Create: `backend-care-admin/src/test/java/com/csu/carenest/careadmin/healthsuggestion/Phase23HealthSuggestionApiTest.java`
- Modify: `backend-care-admin/src/test/resources/schema.sql`
- Modify: `backend-care-admin/src/test/resources/data.sql`

**Interfaces:**
- Consumes: existing login, nurse task, service record, report, archive, permission tables.
- Produces: executable expectations for both fixed endpoints.

- [ ] Write MockMvc tests for assigned nurse creation from a service record and report, admin pagination, archive unchanged, wrong nurse/family denial, forged source `422`, malformed field value `422`, and duplicate `409` with one suggestion/task/log.
- [ ] Add only the Phase 23/24-compatible test tables and permission fixtures needed by those tests.
- [ ] Run `mvn -pl backend-care-admin -Dtest=Phase23HealthSuggestionApiTest test` and confirm failures are missing routes/behavior.

### Task 2: Implement suggestion creation and admin query

**Files:**
- Create: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/healthsuggestion/HealthSuggestionController.java`
- Create: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/healthsuggestion/HealthSuggestionService.java`
- Create: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/healthsuggestion/HealthSuggestionRepository.java`
- Create: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/healthsuggestion/HealthSuggestionDtos.java`

**Interfaces:**
- Produces: `POST /api/v1/orders/{orderId}/health-update-suggestions` and `GET /api/v1/admin/health-review-tasks`.

- [ ] Implement role/assignment checks and source-to-order verification.
- [ ] Parse `newValue` with Jackson into the five frozen field shapes, reject unknown/invalid content, and serialize canonical JSON.
- [ ] Snapshot current Phase 19 values, enforce duplicate detection, and transactionally insert suggestion, review task, backlink, and operation log.
- [ ] Implement role-plus-permission admin filtering and frontend-compatible pagination.
- [ ] Run the targeted test until all Phase 23 cases pass, then run existing care/admin tests.

### Task 3: Freeze database and API contracts

**Files:**
- Modify: `db/schema/phase-23-health-update-suggestion-schema.sql`
- Create: `db/migration/phase-23-health-update-suggestion-contract.sql`
- Modify: `db/seed/phase-19-25-demo-data.sql`
- Modify: care/admin OpenAPI snapshot and generated frontend types using repository scripts.
- Create: `docs/api/phase-23-health-update-suggestions-api.md`
- Create: `docs/stage-check/phase-23-health-update-suggestions.md`
- Modify: `docs/dictionary/phase-19-25-health-data-dictionary.md`

- [ ] Add source/status checks, task linkage, concurrency-safe idempotency key, and review permission provisioning.
- [ ] Update OpenAPI assertions/snapshot and regenerate TypeScript definitions.
- [ ] Document exact validation, 409 semantics, permissions, and no-direct-archive boundary.

### Task 4: Verify full and real workflows

**Files:**
- Modify: `docs/stage-check/phase-23-health-update-suggestions.md`

- [ ] Run `mvn -pl backend-care-admin test` and package both backend modules.
- [ ] Run `pnpm test:stage23`, `pnpm typecheck`, `pnpm contract:check`, and `pnpm build:h5`.
- [ ] Apply the explicit migration to the existing MySQL volume and rebuild `backend-care-admin` without deleting volumes.
- [ ] Through `http://localhost:3000`, log in as nurse/admin, create and query a real suggestion, verify duplicate `409`, wrong-role `403`, one suggestion/task/log, and unchanged archive version/content.
- [ ] Run `git diff --check` and record evidence in the stage-check document.
