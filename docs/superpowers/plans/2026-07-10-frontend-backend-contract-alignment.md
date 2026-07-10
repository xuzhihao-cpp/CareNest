# Frontend-Backend Contract Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align phases 6, 7, 9, and 18 with the real user backend and add automated OpenAPI contract drift checks.

**Architecture:** The backend-generated OpenAPI snapshot is the machine-readable integration contract. Frontend real-mode API functions consume generated TypeScript types, while unimplemented stages remain explicitly mock-only and cannot silently fall back in real mode.

**Tech Stack:** Spring Boot 3.3, Springdoc OpenAPI, MockMvc, Vue 3, TypeScript, uni-app, openapi-typescript, pnpm.

## Global Constraints

- PDF paths, fields, states, database definitions, and demo accounts remain authoritative.
- Do not add backend database fields or tables.
- The frontend follows implemented backend payloads for this integration task.
- Phases without implemented controllers remain `MOCK_ONLY`.
- Real mode must not silently return mock success data.

---

### Task 1: Lock The Implemented Backend Contract

**Files:**
- Modify: `backend-user/pom.xml`
- Create: `backend-user/src/test/java/com/csu/carenest/user/contract/UserApiOpenApiContractTest.java`
- Create: `contracts/user-api-v1.json`

**Interfaces:**
- Produces: Springdoc `/v3/api-docs` and the committed `contracts/user-api-v1.json` snapshot.

- [ ] Add a failing MockMvc test that requires `/v3/api-docs` and verifies phase 6, 7, 9, and 18 paths and schemas.
- [ ] Run `mvn -pl backend-user -Dtest=UserApiOpenApiContractTest test` and confirm it fails because Springdoc is absent.
- [ ] Add `org.springdoc:springdoc-openapi-starter-webmvc-api` to `backend-user/pom.xml`.
- [ ] Generate and commit the normalized OpenAPI snapshot.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: Generate Frontend Contract Types

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Create: `frontend/scripts/generate-user-api-contract.mjs`
- Create: `frontend/src/types/generated/user-api.ts`

**Interfaces:**
- Consumes: `contracts/user-api-v1.json`.
- Produces: generated `paths` and `components` TypeScript types.

- [ ] Add `openapi-typescript` and scripts `contract:generate` and `contract:check`.
- [ ] Implement `contract:check` to generate in memory and compare with the committed output.
- [ ] Run `pnpm --dir frontend contract:check` and confirm it fails before generation.
- [ ] Generate `frontend/src/types/generated/user-api.ts`.
- [ ] Re-run the contract check and confirm it passes.

### Task 3: Align Phases 6, 7, And 9

**Files:**
- Modify: `frontend/src/types/stageSix.ts`
- Modify: `frontend/src/types/stageSeven.ts`
- Modify: `frontend/src/types/stageNine.ts`
- Modify: `frontend/src/api/stageSix.ts`
- Modify: `frontend/src/api/stageSeven.ts`
- Modify: `frontend/src/api/stageNine.ts`
- Modify: `frontend/src/components/StageSixBindingPanel.vue`
- Modify: `frontend/src/components/StageSevenProfilePanel.vue`
- Modify: `frontend/src/components/StageNineServiceAddressPanel.vue`
- Modify: `frontend/src/mock/phase-06/*.json`
- Modify: `frontend/src/mock/phase-07/*.json`
- Modify: `frontend/src/mock/phase-09/*.json`

**Interfaces:**
- Consumes: generated OpenAPI response and request types.
- Produces: backend-shaped arrays and exact mutation payloads.

- [ ] Add compile-time assertions that list responses are arrays, phase 6 approval takes `BindingRequest`, and phase 7 responses contain only `elderId` and `profileVersion`.
- [ ] Run `pnpm --dir frontend typecheck` and confirm the assertions expose the existing mismatches.
- [ ] Replace hand-written real-response types with generated aliases.
- [ ] Adapt component-local pagination and phase 7 form state without inventing response fields.
- [ ] Remove `mockFallback: true` from implemented real endpoints.
- [ ] Run typecheck and confirm it passes.

### Task 4: Align Phase 18 And Mark Mock-Only Stages

**Files:**
- Modify: `frontend/src/types/stageEighteen.ts`
- Modify: `frontend/src/api/stageEighteen.ts`
- Modify: `frontend/src/components/StageEighteenIntegrationPanel.vue`
- Create: `frontend/src/contracts/stageImplementationStatus.ts`

**Interfaces:**
- Produces: separate `HealthResponse` and `DemoDataStatusResponse` models and stage implementation metadata.

- [ ] Add compile-time assertions for the two distinct phase 18 response DTOs and expected stage statuses.
- [ ] Run typecheck and confirm failure against the shared current type.
- [ ] Implement the separate DTOs and mark phases 8 and 10-17 `MOCK_ONLY`.
- [ ] Remove real-mode mock fallback from phase 18.
- [ ] Run typecheck and confirm it passes.

### Task 5: Full Verification And Documentation

**Files:**
- Modify: `docs/api/phase-06-07-09-18-user-api.md`
- Modify: `docs/stage-check/member2-phase-06-07-09-16-18.md`
- Create: `docs/api/frontend-backend-contract-workflow.md`

**Interfaces:**
- Produces: repeatable developer workflow and CI-ready commands.

- [ ] Document contract ownership, update order, and failure handling.
- [ ] Run `pnpm --dir frontend contract:check`.
- [ ] Run `pnpm --dir frontend typecheck`.
- [ ] Run `pnpm --dir frontend build:h5`.
- [ ] Run `mvn -pl backend-user test` and verify all tests pass.
- [ ] Review `git diff`, commit the implementation, and push the feature branch.

