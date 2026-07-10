# Stage 6 Elder Binding Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make family-created pending bindings visible and actionable on the elder frontend using a real database-backed GET endpoint.

**Architecture:** Extend `BindingController` and `UserSideFlowService` with an elder-scoped list operation that reuses `BindingResponse`. Update the stage 6 frontend adapter and panel to select the family or elder list endpoint by role and remove the fabricated fallback.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, JUnit 5/MockMvc, Vue 3, TypeScript, uni-app, OpenAPI.

## Global Constraints

- Add only `GET /api/v1/elder/bindings`; do not add database fields or statuses.
- Resolve elder ownership through `elder_profile.user_id` and query `elder_family_binding.elder_id`.
- Preserve all existing uncommitted user changes in stage 6 frontend and mock files.
- Real mode must never fall back to fabricated binding records.

---

### Task 1: Backend Elder Binding Query

**Files:**
- Modify: `backend-user/src/test/java/com/csu/carenest/user/flow/UserSidePhaseApiTest.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/flow/BindingController.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/flow/UserSideFlowService.java`

**Interfaces:**
- Produces: `UserSideFlowService.elderBindings(String authorization): List<BindingResponse>`
- Produces: `GET /api/v1/elder/bindings -> ApiResponse<List<BindingResponse>>`

- [ ] **Step 1: Write the failing integration tests**

Add a test that creates a binding as `family_demo`, reads the same `bindingId` and `PENDING` status as `elder_demo`, approves it, and verifies both elder and family lists return `ACTIVE`. Add a second assertion that `family_demo` receives 403 from the elder list.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user "-Dtest=UserSidePhaseApiTest#elderReadsRealPendingBindingAndApprovesIt" test
```

Expected: 404 because `GET /api/v1/elder/bindings` is not mapped.

- [ ] **Step 3: Implement the minimal service and controller methods**

The service must require `RoleCode.ELDER`, load `ElderProfile` by `user_id`, query bindings by its `elder_id`, and map each row with `toBindingResponse`. The controller returns the existing `ApiResponse.success(...)` envelope.

- [ ] **Step 4: Run stage 6 tests and verify GREEN**

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user "-Dtest=UserSidePhaseApiTest" test
```

Expected: all `UserSidePhaseApiTest` tests pass.

### Task 2: OpenAPI And Frontend Contract

**Files:**
- Modify: `backend-user/src/test/java/com/csu/carenest/user/contract/UserApiOpenApiContractTest.java`
- Modify: `contracts/user-api-v1.json`
- Modify generated: `frontend/src/types/generated/user-api.ts`
- Modify: `frontend/src/api/mockServerPaths.ts`
- Modify: `docs/api/phase-06-07-09-18-user-api.md`

**Interfaces:**
- Produces: generated operation for `GET /api/v1/elder/bindings` with `BindingResponse[]` data.

- [ ] **Step 1: Assert the new OpenAPI path**

Add `jsonPath("$.paths['/api/v1/elder/bindings'].get").exists()` to the implemented-flow contract test.

- [ ] **Step 2: Refresh and check the committed snapshot**

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user "-DupdateOpenApiSnapshot=true" "-Dtest=UserApiOpenApiContractTest" test
npm --prefix frontend run contract:generate
npm --prefix frontend run contract:check
```

Expected: contract test and generated-file check pass.

- [ ] **Step 3: Register and document the endpoint**

Add `GET /api/v1/elder/bindings` to the stage 6 endpoint registry and API documentation with `ELDER` authorization and `BindingResponse[]` response.

### Task 3: Remove Elder Fabricated Data

**Files:**
- Modify: `frontend/src/api/stageSix.ts`
- Modify: `frontend/src/components/StageSixBindingPanel.vue`

**Interfaces:**
- Produces: `getElderBindings(scenario?: BindingScenario): Promise<ApiResponse<BindingListResult>>`

- [ ] **Step 1: Add the elder list adapter**

Use `/elder/bindings` in real mode. In mock mode return existing records after elder authorization; never create a fallback binding.

- [ ] **Step 2: Switch list loading by role**

Set the default family invite value to `elder_001`. Family role calls `getFamilyBindings`; elder role calls `getElderBindings`. Remove `elderPendingBinding`'s hard-coded fallback.

- [ ] **Step 3: Render approval per real pending row**

For each elder-visible record with `bindingStatus === 'PENDING'`, render a confirm button that passes that exact record to `approveElderBinding`, then reload the elder list.

- [ ] **Step 4: Run full verification**

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user test
npm --prefix frontend run contract:check
npm --prefix frontend run build:h5
```

Expected: backend tests, contract check, TypeScript checking, and H5 build all pass.

- [ ] **Step 5: Verify real MySQL flow**

Log in as `family_demo`, create a binding for `elder_001`, log in as `elder_demo`, read the same pending `bindingId`, approve it, and verify both list endpoints return `ACTIVE`.
