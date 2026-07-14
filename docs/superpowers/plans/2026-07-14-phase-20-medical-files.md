# Phase 20 Medical Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a real two-step medical-file workflow that uploads authorized files to MinIO, records `file_asset` and `medical_file` rows, and lets authorized elders/families reload the persisted review state.

**Architecture:** `backend-user` owns `/api/v1/files` and `/api/v1/elders/{elderId}/medical-files`. A focused storage adapter wraps the MinIO SDK; a JDBC repository persists file ownership and medical metadata; a transactional service enforces the Phase 19 binding/scope matrix and audit logging. The existing Stage 20 frontend remains the contract consumer and receives OpenAPI-generated types.

**Tech Stack:** Java 17, Spring Boot 3.3, JDBC, MinIO Java SDK 8.5, MySQL 8, H2 tests, Vue 3/uni-app/TypeScript, Docker Compose.

## Global Constraints

- Use real multipart upload and MinIO; no base64, localStorage, mock fallback, or browser-visible storage credentials.
- Accept PDF, JPEG, and PNG by verified signature and MIME type; reject empty files and files over 20 MiB.
- New records use wire status `PENDING`; the frontend normalizes it to `PENDING_REVIEW` and displays “待审核”.
- Registration requires `FAMILY + ACTIVE + HEALTH_EDIT`; reading permits elder self or `FAMILY + ACTIVE + HEALTH_VIEW`.
- A `fileId` can only be registered by its uploader, once, and only while it is unbound.
- Do not invent delete/share endpoints. Preview uses a short-lived authorized URL generated only after access checks.

---

### Task 1: Freeze Upload Contract and Storage Adapter

**Files:**
- Modify: `backend-user/pom.xml`
- Modify: `backend-user/src/main/resources/application.yml`
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MedicalFileStorage.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MinioMedicalFileStorage.java`
- Test: `backend-user/src/test/java/com/csu/carenest/user/medicalfile/Phase20MedicalFileApiTest.java`

**Interfaces:**
- Produces: `put(objectKey, MultipartFile)`, `remove(objectKey)`, and `presignedGet(objectKey, Duration)`.
- Consumes: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`.

- [ ] Write multipart API tests for unauthenticated upload, empty file, oversized file, MIME/signature mismatch, and valid PDF.
- [ ] Run `mvn -pl backend-user -Dtest=Phase20MedicalFileApiTest test` and confirm the new endpoint tests fail.
- [ ] Add the MinIO dependency, configuration properties, storage interface, and production MinIO adapter.
- [ ] Implement signature detection for `%PDF`, JPEG SOI, and PNG signature; sanitize display filenames and generate opaque object keys.
- [ ] Run the focused tests and confirm upload validation passes with a mocked storage adapter.

### Task 2: Persist File Assets and Prevent Orphans

**Files:**
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MedicalFileDtos.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MedicalFileRepository.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MedicalFileService.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/medicalfile/MedicalFileController.java`
- Modify: `backend-user/src/test/resources/test-schema.sql`
- Modify: `backend-user/src/test/resources/test-data.sql`
- Test: `backend-user/src/test/java/com/csu/carenest/user/medicalfile/Phase20MedicalFileApiTest.java`

**Interfaces:**
- Produces: upload response `{fileId,url,originalName,mimeType,size,auditStatus}` and persisted `file_asset` ownership.
- Consumes: authenticated `AuthService.CurrentUser` and `MedicalFileStorage`.

- [ ] Add H2 schema/data and failing assertions that valid upload stores owner metadata and storage failure leaves no database row.
- [ ] Implement upload so object creation occurs first, database persistence second, and persistence failure triggers best-effort object removal plus an error log.
- [ ] Write `operation_log` entry `UPLOAD_MEDICAL_FILE_ASSET` without storing file content or credentials.
- [ ] Run focused tests and confirm database ownership and orphan cleanup behavior.

### Task 3: Register and List Medical Files with Permission Matrix

**Files:**
- Modify: the four `medicalfile` production classes from Task 2
- Test: `backend-user/src/test/java/com/csu/carenest/user/medicalfile/Phase20MedicalFileApiTest.java`

**Interfaces:**
- Produces: `POST /api/v1/elders/{elderId}/medical-files` and `GET /api/v1/elders/{elderId}/medical-files`.
- Consumes: `file_asset`, `medical_file`, Phase 19 binding scopes, and short-lived authorized preview URLs.

- [ ] Write failing tests for family registration, elder/family reads, missing scope, inactive binding, wrong uploader, duplicate file binding, invalid type/date/title, and nurse denial.
- [ ] Implement transactional registration with `PENDING`, `REGISTER_MEDICAL_FILE` operation log, and `HEALTH_EDIT` checks.
- [ ] Implement ordered persisted list results with `HEALTH_VIEW` checks; generate preview URLs only after authorization and never return `object_key`.
- [ ] Invalidate affected elder/family home cache keys after successful registration.
- [ ] Run focused tests and verify all permission/status cases pass.

### Task 4: Contract, Schema, Documentation, and Real Docker Verification

**Files:**
- Modify: `backend-user/src/test/java/com/csu/carenest/user/contract/UserApiOpenApiContractTest.java`
- Modify: `contracts/user-api-v1.json`
- Modify: `frontend/src/types/generated/user-api.ts`
- Modify: `db/schema/phase-20-medical-file-schema.sql`
- Create: `db/migration/phase-20-medical-file-contract.sql`
- Create: `docs/api/phase-20-medical-files-api.md`
- Modify: `docs/dictionary/phase-19-25-health-data-dictionary.md`
- Create: `docs/stage-check/phase-20-medical-files.md`

**Interfaces:**
- Produces: frozen API/schema contract and reproducible acceptance evidence.

- [ ] Add OpenAPI snapshot assertions and regenerate TypeScript types; run `pnpm contract:check` and `pnpm typecheck`.
- [ ] Align schema/migration constraints and indexes without deleting existing data; document `PENDING` wire compatibility.
- [ ] Run `mvn -pl backend-user test`, `pnpm typecheck`, `pnpm contract:check`, and Compose config validation sequentially.
- [ ] Rebuild Compose, upload a real PDF through `localhost:3000`, register it, reload the list, and verify MinIO object plus both MySQL rows.
- [ ] Verify unauthorized role/scope responses, file-size/type failures, no storage-key leakage, and all container health checks.
- [ ] Record sanitized request/response, database, object-storage, and permission evidence in the stage-check document.

## Self-Review

- Spec coverage: upload, ownership, MinIO persistence, registration, list refresh, permissions, audit, orphan cleanup, contract, migration, and real verification are assigned.
- Intentional compatibility: wire status stays `PENDING` because Phase 20 schema and Phase 21 consumer already depend on it; frontend maps it to `PENDING_REVIEW`.
- Deferred by contract: delete, replace, sharing, permanent public URLs, and Phase 21 review behavior.
