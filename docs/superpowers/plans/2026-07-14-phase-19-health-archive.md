# Phase 19 Health Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the real Phase 19 health archive APIs against MySQL so the existing elder and family frontend can read and version-safely update the same archived data.

**Architecture:** Add a focused `healtharchive` module to `backend-user` with controller, service, persistence models, and MyBatis mappers. Authorization reuses JWT users and the existing `elder_profile` and `elder_family_binding` tables; all writes use one Spring transaction, optimistic version updates, audit logs, and post-commit home-cache invalidation. The existing frontend contract remains authoritative and receives only business DTOs.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8, H2 integration tests, Vue 3/TypeScript frontend, Docker Compose, Redis 7.

## Global Constraints

- Keep `GET/PUT /api/v1/elders/{elderId}/health-archive` and `POST /api/v1/elders/{elderId}/medications` unchanged.
- MySQL is the source of truth; runtime mock, localStorage fallback, and fake success are forbidden.
- Elder users may read only their own archive; family users need an `ACTIVE` binding plus `HEALTH_VIEW` to read and `HEALTH_EDIT` to write.
- A stale `archiveVersion` returns HTTP 409 without modifying child rows or logs.
- A successful write updates the archive and all child rows atomically, increments the version, writes `health_archive_change_log` and `operation_log`, then invalidates affected home caches after commit.
- Nursing access remains closed in Phase 19; Phase 25 will reuse the response DTO through its assigned-order summary endpoint.
- Do not display IDs, API paths, DTO names, trace IDs, mock labels, or Redis implementation details in user-facing pages.

---

### Task 1: Freeze Persistence And API Contract

**Files:**
- Modify: `db/schema/phase-19-health-archive-schema.sql`
- Create: `db/migration/phase-19-health-archive-contract.sql`
- Modify: `db/seed/phase-19-25-demo-data.sql`
- Create: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveDtos.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveRecords.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveMappers.java`
- Modify: `backend-user/src/test/resources/test-schema.sql`
- Modify: `backend-user/src/test/resources/test-data.sql`

**Interfaces:**
- Produces: `HealthArchiveDtos.ArchiveResponse`, `ArchiveUpdateRequest`, `ArchiveUpdateResult`, `MedicationCreateRequest`, and `MedicationCreateResult` matching `frontend/src/types/stageNineteen.ts`.
- Produces: persistence records for `health_archive`, `chronic_disease`, `medication_plan`, `allergy_record`, `risk_tag`, and `care_plan`.

- [ ] Add an integration-test schema and seed rows for all six Phase 19 tables, including a version-1 archive for `elder_001`.
- [ ] Add a failing DTO validation test proving blank names, duplicate medication data, invalid dates/times, unsupported enum values, and oversized text are rejected with 400 or 422.
- [ ] Run `mvn -pl backend-user -Dtest=Phase19HealthArchiveApiTest test` and confirm failure because the endpoint/module does not exist.
- [ ] Add the DTO and persistence types with exact frontend field names and Bean Validation constraints.
- [ ] Add `risk_tag.tag_code`, elder-scoped uniqueness constraints, and normalized seed values through an idempotent migration and schema baseline update.
- [ ] Run the focused test again and retain the expected endpoint-not-found failure for Task 2.

### Task 2: Read API And Permission Matrix

**Files:**
- Create: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveController.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveService.java`
- Create: `backend-user/src/test/java/com/csu/carenest/user/healtharchive/Phase19HealthArchiveApiTest.java`

**Interfaces:**
- Produces: `HealthArchiveService.getArchive(String authorization, String elderId)`.
- Consumes: existing `AuthService`, `ElderProfileMapper`, `ElderFamilyBindingMapper`, and Phase 19 mappers.

- [ ] Write failing MockMvc tests for elder-self read, active family `HEALTH_VIEW` read, unrelated elder 403, inactive binding 403, missing scope 403, and missing archive 404.
- [ ] Run the focused test and confirm each case fails because the GET endpoint is missing.
- [ ] Implement controller and service read flow, including JSON parsing for medication times and care-plan content and compatibility normalization for existing seed values.
- [ ] Run the focused tests and confirm the read/permission cases pass.
- [ ] Add OpenAPI contract assertions for all three Phase 19 routes and rerun the contract test.

### Task 3: Transactional Full-Archive Update

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveService.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveController.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/healtharchive/Phase19HealthArchiveApiTest.java`

**Interfaces:**
- Produces: `HealthArchiveService.updateArchive(String authorization, String elderId, ArchiveUpdateRequest request)`.

- [ ] Write failing tests for family update success, elder write 403, family without `HEALTH_EDIT` 403, stale version 409, duplicate normalized names 422, and rollback when a child write fails.
- [ ] Run focused tests and confirm failures are due to the missing PUT behavior.
- [ ] Implement a conditional archive-version update followed by child replacement in one transaction; increment version only once.
- [ ] Write before/after JSON to `health_archive_change_log`, write `FAMILY_EDIT` to `operation_log`, and invalidate elder/family home caches after commit.
- [ ] Run focused and complete `backend-user` tests and confirm success.

### Task 4: Versioned Medication Quick Add

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveService.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/healtharchive/HealthArchiveController.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/healtharchive/Phase19HealthArchiveApiTest.java`

**Interfaces:**
- Produces: `HealthArchiveService.addMedication(String authorization, String elderId, MedicationCreateRequest request)`.

- [ ] Write failing tests for successful add, normalized duplicate 422, stale version 409, invalid time/date 400, and permission denial 403.
- [ ] Run the tests and confirm failures are caused by missing POST behavior.
- [ ] Implement version-checked medication insertion, audit logs, and post-commit cache invalidation in one transaction.
- [ ] Run focused and complete backend tests.

### Task 5: Documentation And Frontend Compatibility

**Files:**
- Create: `docs/api/phase-19-health-archive-api.md`
- Modify: `docs/dictionary/phase-19-25-health-data-dictionary.md`
- Modify: `docs/deployment/phase-19b-redis.md`
- Create: `docs/stage-check/phase-19-health-archive.md`
- Verify: `frontend/src/api/stageNineteen.ts`
- Verify: `frontend/src/types/stageNineteen.ts`
- Verify: `frontend/src/components/StageNineteenHealthArchivePanel.vue`

**Interfaces:**
- Documents the exact request, response, permissions, 400/401/403/404/409/422 behavior, transaction boundary, and cache invalidation events.

- [ ] Run `pnpm typecheck` and Phase 19 frontend checks; change frontend code only if the real API exposes a proven mismatch.
- [ ] Document the final API and data mapping, including care-plan JSON compatibility and risk-tag codes.
- [ ] Record automated and real-environment evidence without tokens, passwords, internal object keys, or fabricated results.

### Task 6: Docker Real-Chain Verification

**Files:**
- Modify: `docs/stage-check/phase-19-health-archive.md`

**Interfaces:**
- Verifies the existing Nginx `/api/v1` route to `backend-user` and the current MySQL/Redis services.

- [ ] Run the Phase 19 migration explicitly against the existing Docker database and verify table/column/index state.
- [ ] Rebuild and restart `backend-user` and `frontend` through Compose.
- [ ] Verify family GET/PUT/GET, elder GET, unauthorized family 403, stale-version 409, and medication quick-add through `http://localhost:3000`.
- [ ] Verify database archive version, child rows, both audit logs, and absence of partial writes after a rejected request.
- [ ] Run `mvn -pl backend-user test`, `pnpm typecheck`, relevant frontend scripts, and Docker health checks one final time.

