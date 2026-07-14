# Phase 22 Health Feedback Implementation Plan

## Goal

Deliver the real elder health-feedback workflow used by the existing Stage 22 frontend: elder submits button, text, or voice feedback; an actively bound family member with `HEALTH_VIEW` reads filtered, paginated records; all data is persisted and authorization is enforced server-side.

## Tasks

1. Add API integration tests covering elder identity derivation, role restrictions, binding/scope authorization, validation, severity ordering, voice ownership, voice logs, and high-severity audit behavior.
2. Extend `/api/v1/files` to accept the frontend's validated audio formats while preserving the Phase 20 rule that only PDF/JPEG/PNG assets can be registered as medical files.
3. Implement the Phase 22 controller, service, repository, DTOs, persistence, signed voice URLs, cache invalidation, and audit logging.
4. Harden Phase 22 schema and test schema with enum checks, indexes, and file relationships; update OpenAPI and API/data-dictionary documentation.
5. Run targeted and full backend tests, frontend type/contract checks, Maven packages, then rebuild Docker and verify the real database/API workflow and unauthorized paths.

## Acceptance

- `POST /api/v1/elder/health-feedback` derives the elder from the token and rejects non-elders.
- Voice feedback references an audio asset uploaded by that elder and writes `voice_command_log`.
- `GET /api/v1/family/elders/{elderId}/health-feedback` requires FAMILY + ACTIVE + `HEALTH_VIEW`, filters and paginates, and returns high severity first.
- High severity creates an audit signal only; it does not mutate the health archive or claim notification/diagnosis behavior.
- Existing Phase 20 medical-file tests remain green, and audio cannot be registered as a medical file.
- Docker deployment works through `http://localhost:3000` with persistent MySQL and MinIO data.
