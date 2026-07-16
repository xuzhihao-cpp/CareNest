# AI Safety Routing Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Qwen answer ordinary care questions while deterministic backend rules retain emergency escalation, medical-decision refusal, ticket creation, and unsupported-claim blocking.

**Architecture:** `AiSafetyClassifier` performs only the narrow pre-request hard checks required by phases 41–43. A focused `AiResponseGuard` validates the single Qwen response before `CloudAiProvider` returns it; rejected responses fall back to the existing local safe result without changing API contracts.

**Tech Stack:** Java 17, Spring Boot 3.3, JUnit 5, Java `HttpClient`, Qwen OpenAI-compatible API, Maven.

## Global Constraints

- Keep one Qwen request for each ordinary user message; do not add a second model classification request.
- Preserve `NORMAL`, `WARNING`, and `CRITICAL` response values and existing ticket side effects.
- MySQL remains the source of truth; never persist a rejected cloud response as accepted advice.
- Logs may include only the rejection category, never user text or rejected model content.
- Do not change frontend request or response contracts.

---

### Task 1: Narrow Local Safety Classification

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/AiSafetyClassifier.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/ai/AiSafetyClassifierTest.java`

**Interfaces:**
- Consumes: `AiSafetyClassifier.classify(String)`.
- Produces: the existing `AiProvider.Result` with unchanged fields and safety-level values.

- [ ] **Step 1: Add failing classification tests**

Add assertions that `血压应该怎么记录？`, `忘记吃药怎么办？`, and `最近肚子痛` are `NORMAL`; assert `能不能把降压药加量？`, `我可以停药吗？`, and `请帮我诊断是什么病` are `WARNING`; retain the existing death-risk `CRITICAL` assertion.

- [ ] **Step 2: Run the classifier test and verify it fails**

Run: `mvn -pl backend-user -Dtest=AiSafetyClassifierTest test`

Expected: FAIL because broad tokens `药`, `血压`, and `症状` currently classify ordinary questions as `WARNING`.

- [ ] **Step 3: Replace broad warning tokens with explicit medical-decision phrases**

Keep the current critical terms. Replace the warning branch with explicit phrases covering medication start/stop/replacement/dose changes and diagnosis/prescription/treatment decisions, for example:

```java
if (contains(text,
        "加药", "加量", "减药", "减量", "停药", "换药", "改药", "调整剂量",
        "能不能吃", "可以吃什么药", "该吃什么药", "吃多少", "用量",
        "诊断", "确诊", "处方", "治疗方案", "怎么治疗", "是什么病")) {
    return warningResult();
}
```

- [ ] **Step 4: Run the classifier test and verify it passes**

Run: `mvn -pl backend-user -Dtest=AiSafetyClassifierTest test`

Expected: all classifier cases pass.

### Task 2: Extract And Extend Cloud Response Guard

**Files:**
- Create: `backend-user/src/main/java/com/csu/carenest/user/ai/AiResponseGuard.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/ai/CloudAiProvider.java`
- Create: `backend-user/src/test/java/com/csu/carenest/user/ai/AiResponseGuardTest.java`
- Modify: `backend-user/src/test/java/com/csu/carenest/user/ai/CloudAiProviderTest.java`

**Interfaces:**
- Produces: `Optional<String> AiResponseGuard.rejectionReason(String answer)` where the value is `PLATFORM_CLAIM` or `MEDICAL_DECISION`.
- Consumes: `CloudAiProvider` injects `AiResponseGuard` and falls back to the precomputed local result when a reason is present.

- [ ] **Step 1: Add failing response-guard tests**

Test that platform promises (`24小时在线`, `400-XXX-XXXX`, `全程陪伴`) return `PLATFORM_CLAIM`; diagnosis and medication instructions (`你这是胃炎`, `建议立即停药`, `每天服用两片`) return `MEDICAL_DECISION`; ordinary care advice returns empty.

- [ ] **Step 2: Run the guard test and verify it fails to compile**

Run: `mvn -pl backend-user -Dtest=AiResponseGuardTest test`

Expected: FAIL because `AiResponseGuard` does not exist.

- [ ] **Step 3: Implement the focused guard**

Create a Spring component that normalizes whitespace, checks the existing platform-claim patterns, then checks explicit diagnosis, medication-change, and dose-instruction patterns. Return a category only; never log or return the rejected content.

- [ ] **Step 4: Inject the guard into the cloud provider**

Replace `containsUnsupportedPlatformClaim` with:

```java
var rejection = responseGuard.rejectionReason(answer);
if (answer.isEmpty() || rejection.isPresent()) {
    rejection.ifPresent(reason -> log.warn("AI provider response rejected: {}", reason));
    return safety;
}
```

Update provider tests to construct and inject the real guard. Add a test whose fake cloud response contains a medication instruction and assert the local safe fallback is returned.

- [ ] **Step 5: Run focused AI tests**

Run: `mvn -pl backend-user '-Dtest=AiSafetyClassifierTest,AiResponseGuardTest,CloudAiProviderTest' test`

Expected: all focused tests pass and the test server receives exactly one request for an ordinary question.

### Task 3: Full Verification And Runtime Deployment

**Files:**
- Verify only; no additional production files.

**Interfaces:**
- Consumes: unchanged `/api/v1/ai/sessions/{sessionId}/messages` API.
- Produces: rebuilt healthy `backend-user` Docker service.

- [ ] **Step 1: Run complete user-backend tests**

Run: `mvn -pl backend-user test`

Expected: zero failures and zero errors.

- [ ] **Step 2: Check formatting and staged scope**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; unrelated untracked user files remain unstaged.

- [ ] **Step 3: Rebuild the user backend**

Run: `docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build backend-user`

Expected: image builds and `carenest-backend-user-1` becomes healthy.

- [ ] **Step 4: Verify runtime health**

Run: `Invoke-RestMethod http://localhost:3000/api/v1/health`.

Expected: response code `0` and status `UP`.

- [ ] **Step 5: Commit implementation**

Stage only the classifier, response guard, and AI tests, then commit with:

```text
feat: optimize ai safety routing
```
