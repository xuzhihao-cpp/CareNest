# AI Conversation History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and restore AI conversation history with a selectable server-backed session list while creating automatic tickets only for clear emergencies.

**Architecture:** Extend the existing AI controller/service/repository with authorized session and message reads over the existing MySQL tables. Keep the current write endpoints, update session activity when messages are stored, and let the Vue panel resolve its active session from server history plus a locally cached session ID. The local classifier becomes emergency-only; every non-critical input reaches the cloud provider and remains protected by the response guard.

**Tech Stack:** Java 17, Spring Boot 3, JdbcTemplate, JUnit 5, Mockito, Vue 3, TypeScript, uni-app, Vite, Docker Compose, Playwright.

## Global Constraints

- Do not add a database table or use Redis as the conversation source of truth.
- Preserve existing create-session, send-message, and close-session response compatibility.
- Only `CRITICAL` input creates assistance and customer-service tickets.
- All non-critical questions call the configured cloud provider; cloud output safety guards remain enabled.
- Elder users see only their sessions; family users see only actively bound elders.
- Do not add delete, rename, search, export, streaming, or model-selection features.

---

### Task 1: Authorized conversation history API

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiAssistantDtos.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiAssistantRepository.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiAssistantService.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiAssistantController.java`
- Create: `backend-user/src/test/java/com/csu/carenest/user/ai/AiConversationHistoryServiceTest.java`

**Interfaces:**
- Produces: `PageResult<SessionSummary> listSessions(String token, String elderId, int page, int size)`
- Produces: `List<ConversationMessage> messages(String token, String sessionId)`
- Produces: `GET /api/v1/ai/sessions` and `GET /api/v1/ai/sessions/{id}/messages`

- [ ] **Step 1: Write failing service tests**

Cover elder ownership, family active-binding filtering, unauthorized message access, pagination validation, and ascending message order. Use DTO expectations equivalent to:

```java
assertEquals("session-2", result.records().get(0).sessionId());
assertEquals("ASSISTANT", messages.get(1).senderRole());
assertThrows(ApiException.class, () -> service.messages("Bearer family", "other-session"));
```

- [ ] **Step 2: Run tests and confirm the missing methods fail compilation**

Run: `mvn -pl backend-user '-Dtest=AiConversationHistoryServiceTest' test`

Expected: failure because list/history service methods and DTOs do not exist.

- [ ] **Step 3: Add DTOs and parameterized repository queries**

Add these records:

```java
public record SessionSummary(String sessionId, String elderId, String elderName,
    String sessionTitle, String sessionStatus, String safetyLevel,
    String latestMessagePreview, String createdAt, String updatedAt) {}
public record ConversationMessage(String messageId, String senderRole,
    String messageType, String content, boolean safetyFlag, String createdAt) {}
```

Repository list queries must join `elder_profile`, select the latest message summary with a correlated subquery, filter elder sessions by owner, filter family sessions through `elder_family_binding.binding_status='ACTIVE'`, order by `s.updated_at DESC`, and use `LIMIT ? OFFSET ?`. Message queries order by `created_at ASC, message_id ASC`.

- [ ] **Step 4: Add authorization-aware service and controller methods**

Validate `page >= 1`, `1 <= size <= 50`. Elder requests ignore foreign elder IDs and remain self-scoped. Family requests accept an optional active-bound elder filter. Reuse `authorizeSession` for message history.

- [ ] **Step 5: Run focused tests**

Run: `mvn -pl backend-user '-Dtest=AiConversationHistoryServiceTest' test`

Expected: all history service tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend-user/src/main/java/com/csu/carenest/user/ai backend-user/src/test/java/com/csu/carenest/user/ai/AiConversationHistoryServiceTest.java
git commit -m "feat: add ai conversation history api"
```

### Task 2: Emergency-only local routing

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/CloudAiProvider.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiAssistantRepository.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/ai/CloudAiProviderTest.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/ai/AiAssistantServiceTest.java`

**Interfaces:**
- Consumes: existing `AiSafetyClassifier.classify(String)` and `AiResponseGuard.rejectionReason(String)`
- Produces: cloud invocation for every result except `CRITICAL`
- Produces: updated `ai_assistant_session.updated_at` after each message exchange

- [ ] **Step 1: Write failing routing tests**

Verify explicit medication and diagnosis questions make exactly one HTTP request and return no local `WARNING`; verify `CRITICAL` makes zero HTTP requests. Verify service results create no ticket for normal/fallback responses and both ticket types for `CRITICAL`.

- [ ] **Step 2: Run focused tests and observe current warning short-circuit**

Run: `mvn -pl backend-user '-Dtest=CloudAiProviderTest,AiAssistantServiceTest' test`

Expected: non-critical medical questions fail the request-count assertion.

- [ ] **Step 3: Change cloud routing and activity update**

Use only the critical branch as the local short circuit:

```java
Result safety = classifier.classify(prompt);
if ("CRITICAL".equals(safety.safetyLevel())) return safety;
```

Keep response-guard fallback as `NORMAL` so it cannot create a ticket. Ensure the repository session update remains in the same message transaction and always writes `updated_at=CURRENT_TIMESTAMP`.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl backend-user '-Dtest=CloudAiProviderTest,AiAssistantServiceTest,AiSafetyClassifierTest,AiResponseGuardTest' test`

Expected: all routing and safety tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend-user/src/main/java/com/csu/carenest/user/ai backend-user/src/test/java/com/csu/carenest/user/ai
git commit -m "feat: route non-emergency ai questions to cloud"
```

### Task 3: Frontend conversation list and restoration

**Files:**
- Modify: `frontend/src/types/stageFortyOne.ts`
- Modify: `frontend/src/api/stageFortyOne.ts`
- Modify: `frontend/src/components/StageFortyOneAiAssistantPanel.vue`

**Interfaces:**
- Consumes: `GET /ai/sessions?page&size&elderId` and `GET /ai/sessions/{id}/messages`
- Consumes: existing `getFamilyElders()` for active family elder selection
- Produces: session list, active-session restoration, new-conversation action, and server-loaded messages

- [ ] **Step 1: Add API contracts**

Define `AiSessionSummary`, `AiConversationMessage`, and `AiSessionPage`, then add:

```ts
export const listAiSessions = (elderId?: string) => request<AiSessionPage>({
  url: `/ai/sessions?page=1&size=50${elderId ? `&elderId=${encodeURIComponent(elderId)}` : ''}`,
  method: 'GET'
})
export const getAiSessionMessages = (id: string) =>
  request<AiConversationMessage[]>({ url: `/ai/sessions/${id}/messages`, method: 'GET' })
```

- [ ] **Step 2: Replace component-memory-only initialization**

On mount, load family elders when applicable, then load sessions. Resolve the active ID from `carenest_ai_active_session_<role>_<elder>` only when that ID exists in the returned list; otherwise select the first session. Load its messages and map `USER`/`ASSISTANT` to the current bubble roles.

- [ ] **Step 3: Add list and new-conversation controls**

Use an icon button for history, a compact session drawer/list, and a `Plus` icon button for new conversation. Keep session title, preview, and formatted activity time scannable. Do not nest cards or expose IDs.

- [ ] **Step 4: Preserve history during send and navigation**

Create a session only when no active session exists. After a successful send, append the response, refresh session summaries, keep the current ID selected, and persist only that ID locally. On send failure, retain the unsent text/error state without replacing loaded history.

- [ ] **Step 5: Type-check frontend**

Run: `pnpm typecheck` from `frontend`.

Expected: zero TypeScript errors.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/types/stageFortyOne.ts frontend/src/api/stageFortyOne.ts frontend/src/components/StageFortyOneAiAssistantPanel.vue
git commit -m "feat: restore ai conversation history"
```

### Task 4: Full-stack verification

**Files:**
- Test only; no planned production file changes.

**Interfaces:**
- Verifies all interfaces produced by Tasks 1-3 through Docker's `localhost:3000` route.

- [ ] **Step 1: Run backend and frontend verification**

Run:

```powershell
mvn -pl backend-user test
cd frontend
pnpm typecheck
```

Expected: all backend tests and frontend type checks pass.

- [ ] **Step 2: Rebuild Docker services**

Run:

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build backend-user frontend
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml ps
```

Expected: backend-user and frontend are healthy.

- [ ] **Step 3: Verify real API persistence and authorization**

With `elder_demo`, create two sessions, send messages, list sessions, and read each history. Confirm MySQL rows persist and list ordering follows latest activity. Confirm a normal medical question creates zero tickets and a clear emergency creates one assistance and one customer-service ticket. Delete only the generated verification rows afterward.

- [ ] **Step 4: Verify browser workflow with Playwright**

At `http://localhost:3000`, log in as `elder_demo`, send a message, switch away from AI, return, refresh, create a second conversation, and switch between both histories. Check desktop and mobile widths for overlap, blank states, and console errors.

- [ ] **Step 5: Review and final commit if verification required fixes**

Run `git diff --check` and ensure only intended files are tracked. Commit any verification fixes with a scoped message before reporting completion.
