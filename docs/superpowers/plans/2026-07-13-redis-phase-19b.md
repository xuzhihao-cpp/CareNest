# Redis Phase 19-B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed, fail-open read caches and short write locks to the two CareNest backend services while MySQL remains the sole business source of truth.

**Architecture:** Each independent Spring Boot module receives the same small Redis infrastructure package: configuration, JSON cache helper, hashed-user key factory, and ownership-token short lock helper. Existing services retain their authorization and MySQL reads; they invoke cache operations only after authorization and only invalidate after a successful transaction. Redis failures are swallowed by the infrastructure and never turn a read or write into a false success.

**Tech Stack:** Spring Boot 3, Spring Data Redis/Lettuce, Jackson, MySQL, JUnit 5, Docker Compose Redis 7.

## Global Constraints

- Redis keys use the `carenest:` prefix and explicit `:v1` version suffixes.
- MySQL remains the only source of truth for orders, reports, bindings, profiles and review tasks.
- Cache access occurs only after authentication, resource ownership and role/scope checks.
- Redis must not contain plaintext passwords, JWTs, phone numbers, complete medical records, MinIO keys or logged values.
- On Redis failure, reads fall back to MySQL; writes rely on the existing database transaction and conditional/status checks.
- Use `REDIS_HOST` and `REDIS_PORT`, 2-second command/connect timeouts, and JSON serialization.
- Short-lock contention maps to the existing `409 Conflict` behavior; lock ownership is token based and release must not delete another caller's lock.
- Do not change public API paths, DTOs, role/status dictionaries, JWT design, or add Redis-facing UI.
- Do not modify `main`, do not overwrite unrelated changes, and do not use runtime mock data.

---

### Task 1: Add fail-open Redis infrastructure to both backends

**Files:**
- Modify: `backend-user/pom.xml`, `backend-user/src/main/resources/application.yml`
- Modify: `backend-care-admin/pom.xml`, `backend-care-admin/src/main/resources/application.yml`
- Create: `backend-user/src/main/java/com/csu/carenest/user/redis/RedisConfiguration.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/redis/RedisCacheService.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/redis/RedisLockService.java`
- Create: `backend-user/src/main/java/com/csu/carenest/user/redis/RedisKeyFactory.java`
- Create equivalent files under `backend-care-admin/src/main/java/com/csu/carenest/careadmin/redis/`
- Test: cache/key/lock tests in both module test trees.

**Interfaces:**
- Produces `RedisCacheService.get(String key, Class<T> type)`, `put(String key, Object value, Duration ttl)`, `evict(String key)` and `evict(Collection<String> keys)`.
- Produces `RedisLockService.tryAcquire(String key, Duration ttl): Optional<LockHandle>` and `LockHandle.close()`.
- Produces `RedisKeyFactory.userHash(String userId)` as a SHA-256 hexadecimal digest and cache/lock key builders.

- [ ] **Step 1: Write failing infrastructure tests**

Create tests using mocked `StringRedisTemplate` that prove a cache read deserializes JSON, Redis exceptions return `Optional.empty()`, a cache write exception is ignored, the same user id hashes deterministically without appearing in the key, and lock release calls the compare-and-delete Lua script with its ownership token.

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*Redis*Test' test`

Expected: compile/test failures because the Redis package and Spring Data Redis dependency do not exist.

- [ ] **Step 3: Add minimal Redis configuration and helpers**

Add `spring-boot-starter-data-redis`, configure Lettuce with `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` and 2-second timeouts, create a `StringRedisTemplate`, and implement helpers that catch `RedisConnectionFailureException`, `DataAccessException`, and serialization exceptions. Store JSON only through the existing module `ObjectMapper`; use `SET key value NX PX` for acquisition and a Lua compare/delete script for release.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*Redis*Test' test`

Expected: all Redis infrastructure tests pass in both modules.

- [ ] **Step 5: Commit the independently testable infrastructure**

Run: `git add backend-user backend-care-admin && git commit -m "feat: add fail-open Redis infrastructure"`

### Task 2: Cache public on-shelf service items and invalidate after service-item writes

**Files:**
- Modify: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/phase/CareAdminPhaseService.java`
- Test: `backend-care-admin/src/test/java/com/csu/carenest/careadmin/phase/CareAdminPhaseServiceTest.java`

**Interfaces:**
- Consumes `RedisCacheService` and `RedisKeyFactory.onShelfServiceItemsKey()`.
- Produces a five-minute cache at `carenest:service-items:on-shelf:v1` only for `serviceItems(false)`.

- [ ] **Step 1: Write failing service-item cache tests**

Add tests proving `serviceItems(false)` returns the Redis value on a hit, queries MySQL then stores the value for five minutes on a miss, and each create/update/delete operation invalidates the on-shelf key only after the transactional write succeeds. Include an assertion that the admin `includeOffShelf=true` listing is never served by the public cache.

- [ ] **Step 2: Run the focused test to verify RED**

Run: `mvn -pl backend-care-admin -Dtest=CareAdminPhaseServiceTest test`

Expected: the new cache assertions fail because the service always queries MySQL and never evicts Redis.

- [ ] **Step 3: Implement the smallest service-item cache integration**

Inject the cache helper into `CareAdminPhaseService`. Read the public cache only inside `serviceItems(false)`, fall back to the existing SQL query, cache the result for `Duration.ofMinutes(5)`, and call `evict(onShelfServiceItemsKey())` after each successful service-item database mutation.

- [ ] **Step 4: Run the focused test to verify GREEN**

Run: `mvn -pl backend-care-admin -Dtest=CareAdminPhaseServiceTest test`

Expected: service-item cache hit, miss, and post-write invalidation tests pass.

- [ ] **Step 5: Commit the service-item cache task**

Run: `git add backend-care-admin && git commit -m "feat: cache on-shelf service items"`

### Task 3: Cache role-scoped home summaries and add explicit invalidation APIs

**Files:**
- Modify: `backend-user/src/main/java/com/csu/carenest/user/status/StatusService.java`
- Modify: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/phase/CareAdminPhaseService.java`
- Modify: existing user-side binding/report and care/admin order/task write services that own the state change.
- Test: focused status, report, binding, order, and task service tests.

**Interfaces:**
- Produces `HomeCacheInvalidator.evict(RoleCode role, String userId)` and owner-specific invalidation helpers.
- Uses `carenest:home:{role}:{userHash}:v1` with a 30-second TTL.

- [ ] **Step 1: Write failing home-cache and authorization-order tests**

Add tests that authenticate and verify role/scope before invoking the cache, cache each permitted user's own summary for 30 seconds, do not share keys between users, and evict the affected elder/family/nurse/admin summaries after a committed binding, report, order, task, feedback, or authorization write.

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*Status*Test,*ReportAck*Test,*CareAdminPhase*Test' test`

Expected: the new cache and invalidation assertions fail because home summaries directly query MySQL and no write service exposes invalidation.

- [ ] **Step 3: Implement summary caching and post-commit invalidation**

Cache only the final DTO after `AuthService.requireCurrentUser`, role validation, and family binding/scope validation. Add transaction-synchronization based post-commit eviction so rollback paths perform no cache mutation. Invalidation must target only identities linked to the changed order/report/task/binding and must never construct a key from a phone number or display name.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*Status*Test,*ReportAck*Test,*CareAdminPhase*Test' test`

Expected: permission-first cache access, user isolation, and committed-write invalidation tests pass.

- [ ] **Step 5: Commit the home-summary cache task**

Run: `git add backend-user backend-care-admin && git commit -m "feat: cache role-scoped home summaries"`

### Task 4: Add short locks to existing order and report state transitions

**Files:**
- Modify: `backend-care-admin/src/main/java/com/csu/carenest/careadmin/phase/CareAdminPhaseService.java`
- Modify: `backend-user/src/main/java/com/csu/carenest/user/report/ReportAckService.java`
- Test: focused concurrent-transition tests for the two services.

**Interfaces:**
- Uses `carenest:lock:order:{orderId}` for dispatch, task/order status changes, cancellation and rescheduling.
- Uses `carenest:lock:report:{reportId}` for report confirmation and regeneration.
- Lock TTL is 20 seconds; lock contention throws the existing conflict exception.

- [ ] **Step 1: Write failing lock/contention tests**

Add tests that simulate an unavailable lock and expect the existing `ConflictException`, then simulate a held lock while dispatching the same `WAIT_DISPATCH` order or confirming/regenerating the same report. Assert only one database state/log write occurs. Add a Redis-failure test showing the code still uses its MySQL state/transaction path rather than returning success without a database mutation.

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*ReportAck*Test,*CareAdminPhase*Test' test`

Expected: contention assertions fail because no short lock exists around the transitions.

- [ ] **Step 3: Implement lock boundaries with database as final arbiter**

Acquire the matching short lock after authorization/resource checks and before the existing status check/update. Use try-with-resources for lock release. Do not replace existing transaction, status validation, or database update paths; if Redis is unavailable, continue to those database safeguards. Invalidate relevant home summaries only after the transaction commits.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run: `mvn -pl backend-user,backend-care-admin -Dtest='*ReportAck*Test,*CareAdminPhase*Test' test`

Expected: all contention and Redis-failure safety tests pass.

- [ ] **Step 5: Commit the short-lock task**

Run: `git add backend-user backend-care-admin && git commit -m "feat: protect order and report transitions with Redis locks"`

### Task 5: Record cache ownership, validate Docker failure behavior, and capture evidence

**Files:**
- Create: `docs/deployment/phase-19b-redis.md`
- Create: `docs/stage-check/phase-19b-redis.md`
- Modify only if required: `docker/env/.env.example`, Docker Compose configuration.

**Interfaces:**
- Documents every `carenest:` key, owner, TTL, invalidation event, sensitivity classification, and behavior when Redis is unavailable.

- [ ] **Step 1: Write a verification checklist before changing documentation**

List exact commands and expected observations for cache miss/hit, service-item write invalidation, binding/scope invalidation, Redis outage fallback, concurrent dispatch/report confirmation, Redis key scanning, and database/log verification.

- [ ] **Step 2: Run the automated backend regression suite**

Run: `mvn -pl backend-user,backend-care-admin test`

Expected: all module tests pass.

- [ ] **Step 3: Execute real Docker Compose checks**

Run the documented 19-A Compose commands, inspect Redis with `redis-cli --scan --pattern 'carenest:*'`, stop only the project Redis service, verify permitted reads still return MySQL-backed results and protected writes retain database conflict guarantees, then restore Redis and verify cache repopulation.

- [ ] **Step 4: Write deployment and acceptance evidence**

Document the exact key matrix, environment variables, safe Redis outage semantics, real API response summaries, DB query results, conflict outcomes, and screenshot paths. Do not include raw bearer tokens, passwords, cache values, phone numbers, medical content, or MinIO credentials.

- [ ] **Step 5: Commit documentation and evidence**

Run: `git add docs docker && git commit -m "docs: document Redis consistency controls"`

## Plan Self-Review

- Spec coverage: Tasks 1-4 cover Redis configuration, safe serialization, service-item and home caches, post-commit invalidation, lock protection and MySQL fallback. Task 5 covers the required key policy and Docker/API/DB evidence.
- Intentional boundary: phase 19/24 archive locks are supplied by the reusable `RedisLockService`; no nonexistent health-archive endpoint is added in 19-B.
- Type consistency: both modules expose the same cache, lock and key factory contracts; business services consume only those contracts.
- Placeholder scan: no deferred implementation placeholders or unspecified verification commands remain.
