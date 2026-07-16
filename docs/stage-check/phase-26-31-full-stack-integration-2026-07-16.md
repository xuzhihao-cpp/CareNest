# Phase 26-31 Full-Stack Integration Acceptance

Date: 2026-07-16
Branch: phase-26-31/full-stack-integration
Base main revision: a78a6f4
Timezone: Asia/Shanghai

## Scope

This acceptance covers the integrated frontend, care-admin backend, MySQL schema and seed data, Redis cache, MinIO file storage, Docker runtime, and real API flows for phases 26 through 31.

The work was performed after pulling the latest main branch. No runtime mock data was used in the tested phase 26-31 flows.

## Runtime

The Docker compose project was started with the repository compose files and the local environment file. The following services were healthy:

- frontend: http://127.0.0.1:5173
- backend-user: http://127.0.0.1:18081
- backend-care-admin: http://127.0.0.1:18082
- MySQL: 127.0.0.1:33061
- Redis: 127.0.0.1:36379
- MinIO API: http://127.0.0.1:39000
- MinIO console: http://127.0.0.1:39001

The database was initialized from clean Docker volumes. Phase 26-31 schema and seed scripts completed without SQL errors. Circular demonstration data in the earlier seed set was made safe for clean initialization by temporarily disabling foreign-key checks around that data block.

## Main implementation changes

### Backend and API

- Added permission enforcement through the real permission service for nurse qualification submission, qualification review, training review, recommendation viewing, preference selection, and attention acknowledgement.
- Expanded qualification application, qualification file, skill, and training DTOs to the rich read model required by the later phases.
- Added real skill dictionary reads and seeded the `nurseServiceSkill` dictionary with business-readable names.
- Added qualification file ownership, status, MIME type, size, and nurse ownership checks.
- Allowed a newly uploaded pending file to be registered once by its owner while still rejecting foreign-file reuse.
- Added training status reads and review updates, including qualification and expiry checks and nurse order eligibility updates.
- Added real nurse recommendation filtering for qualification, valid training, active account/profile, enabled skills, schedule conflicts, and active task/order conflicts.
- Persisted recommendation logs in MySQL and added a Redis cache with a maximum five-minute TTL and prefix eviction.
- Added preferred nurse snapshot fields to order detail and order creation flows.
- Revalidated qualification, training, skill, and schedule eligibility during dispatch.
- Added phase 31 attention acknowledgement gating before a task can enter service execution.
- Added readable Chinese recommendation reasons and removed technical codes from the user-facing read model.

### Database and storage

- Added the nurse skill dictionary table and seed entries.
- Added MinIO initialization for the qualification demonstration asset.
- Verified that qualification files are stored in MinIO and that the application can preview the stored object through the protected backend route.
- Verified operation log entries for upload, qualification submission, qualification review, training review, order creation, dispatch, and attention acknowledgement.

### Frontend

- Connected phases 26-31 pages to the real endpoints and real permission response shape (`data.permissions`).
- Kept qualification retry file credentials durable across component remounts and prevented duplicate registration after a successful server-side registration.
- Fixed H5 storage adapter usage so qualification status loading works in the production Docker frontend.
- Added readable skill names, qualification/training status presentation, recommendation reasons, preferred nurse details, and attention acknowledgement states.
- Preserved user-facing Chinese labels and avoided API paths, DTO names, trace IDs, and internal identifiers in the tested pages.
- Updated the phase 28 frontend contract and workbench behavior to use the real training read model.

## Automated verification

All commands below passed:

- `mvn -q clean test`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend test:stage26` (9/9)
- `pnpm --dir frontend test:stage27` (8/8)
- `pnpm --dir frontend test:stage28` (6/6)
- `pnpm --dir frontend test:stage29` (8/8)
- `pnpm --dir frontend test:stage30` (10 API/rule tests and 3 component tests)
- `pnpm --dir frontend test:stage31` (7 API/rule tests and 8 component tests)
- `pnpm --dir frontend build:h5`
- `git diff --check`

## Real API acceptance

### Authentication and permissions

Real login was verified for elder, family, nurse, customer service, and admin demo accounts. The permission endpoint was read from the running service and used the `data.permissions` field.

- ADMIN: qualification review, training review, recommendation view, attention review
- CUSTOMER_SERVICE: qualification review, training review, attention review
- FAMILY: recommendation view, preference selection
- NURSE: qualification submit, recommendation view, attention acknowledgement

Unauthorized role combinations returned 403 in the tested flows.

### Phase 26: nurse qualification admission

- Nurse qualification status returned the rich application, file, skill, and training fields.
- Admin and customer service could read the pending application list.
- Admin certificate preview returned HTTP 200 and a real 668-byte PDF from MinIO.
- Qualification review changed the application to APPROVED and the nurse read model reflected the new state.
- A new nurse uploaded a PDF and registered a qualification successfully after the pending-file ownership fix.
- A different nurse attempting to use that file returned HTTP 403.

### Phase 27-28: qualification and training review

- Admin training review required an approved qualification and a future expiry time.
- Training review succeeded and the nurse read model returned APPROVED with the new expiry.
- Invalid or expired training was excluded from dispatch/recommendation eligibility.

### Phase 29: recommendation

- Family recommendation request returned three real candidates for the tested elder, service, address, and future appointment time.
- Candidates were available and their reasons were business-readable Chinese text without skill codes.
- Recommendation logs were persisted in MySQL.
- Redis contained a recommendation cache key with a TTL within the five-minute limit.

### Phase 30: preference and dispatch

- Family created a real order with a selected preferred nurse.
- Order creation response and subsequent order detail both contained the preferred nurse name and reason.
- Recommendation read-back returned the saved candidate set.
- Admin invalid dispatch to an expired nurse returned HTTP 422.
- Valid dispatch succeeded and the order read model changed to DISPATCHED.

### Phase 31: attention acknowledgement and service gate

- Admin and customer service could read the attention notices.
- Other tested roles were rejected with HTTP 403.
- Before acknowledgement, changing a task to SERVING returned HTTP 422.
- All required notices were acknowledged by the assigned nurse.
- After the required acknowledgements, the task and order could enter SERVING.

## Database and cache evidence

- Phase 26-31 tables and permission seeds were present after clean initialization.
- The newly registered nurse qualification and file were stored with PENDING status and the correct uploading nurse.
- Approved nurse qualification and training records were present and `can_take_order` was enabled after review.
- Recommendation logs contained six rows across two request keys for the exercised recommendation requests.
- The integration order stored the preferred nurse snapshot and recommendation reason.
- The attention gate stored three required acknowledgements and reported zero required acknowledgements remaining.
- Redis recommendation key count was nonzero after a real recommendation request and its TTL was 277 seconds at verification time.
- MinIO object verification succeeded for the uploaded PDF asset.

## Visual verification

The Docker H5 frontend was opened in the browser and checked for:

- admin qualification list and qualification detail
- admin training review list
- nurse qualification status and upload result
- family service order list and order detail
- family recommendation and preferred nurse display
- readable phase 31 attention workflow

No API path, DTO label, trace ID, or raw internal response was visible in the checked user-facing views.

## Test data note

Real integration tests intentionally changed demo data: one qualification was approved, one training record was approved, one new qualification remains pending, one order was created and dispatched, and one phase 31 task was moved to SERVING after all required notices were acknowledged. These are expected verification mutations in the running Docker database.

## Result

The phase 26-31 integrated implementation passed the automated project test suite, frontend type/build checks, clean database initialization, real role/API checks, Redis and MinIO checks, Docker health checks, and browser smoke verification.
