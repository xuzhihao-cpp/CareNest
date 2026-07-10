# Frontend and Backend Contract Alignment Check

## Source state

- `origin/main` contains the phase 5-18 frontend merge at commit `40732f8`.
- `backend-user` currently implements the real endpoints for phases 6, 7, 9, and 18.
- Phases 8 and 10-17 remain mock-only because this repository has no corresponding backend controllers.

## Resolved mismatches

- Phases 6, 7, and 9 list responses are arrays, matching the Java controller return types.
- Phase 6 approval sends the required `BindingRequest` body.
- Phase 7 response types contain only `elderId` and `profileVersion`; edit form fields remain request-only UI state.
- Phase 18 uses separate `HealthResponse` and `DemoDataStatusResponse` models.
- Real mode no longer falls back to successful mock responses for phases 8 and 10-17.

## Repeatable evidence

- Backend OpenAPI contract test: `UserApiOpenApiContractTest`, 2 tests passed.
- Frontend contract check: `pnpm --dir frontend contract:check`, passed.
- Frontend typecheck: `pnpm --dir frontend typecheck`, passed.
- Frontend H5 build: `pnpm --dir frontend build:h5`, passed.

The committed OpenAPI snapshot and generated TypeScript types are the drift gate for future frontend/backend changes.
