# Frontend-Backend Contract Alignment Design

## Goal

Align the phase 5-18 frontend with the implemented user backend for phases 6, 7, 9, and 18, while keeping phases without a real backend explicitly mock-only. Add a repeatable contract workflow so model drift fails during verification instead of appearing during manual integration.

## Sources Of Truth

Contract decisions use this order:

1. The member kickoff PDFs define allowed paths, fields, and states.
2. Implemented backend controllers and DTOs define the current real HTTP payload shape.
3. Frontend types and mock files adapt to those contracts and may not invent real-response fields.

When the PDF and implementation disagree, the discrepancy is documented and the frontend follows the current backend for integration, as requested. The backend discrepancy remains visible instead of being hidden by a frontend fallback.

## Current Findings

- The frontend merge itself is conflict-free; `origin/main` contains commit `40732f8`.
- Phase 6, 7, and 9 list endpoints return JSON arrays from the backend, while the frontend expects `PageResult`.
- Phase 6 approval requires `BindingRequest`, while the frontend sends no body.
- Phase 7 backend responses contain only `elderId` and `profileVersion`; the frontend treats request fields as response fields.
- Phase 18 health and demo-data endpoints return different DTOs, while the frontend uses one shared type.
- Phases 8 and 10-17 have frontend mock flows but no corresponding backend implementation in this repository.

## Design

### Real Endpoint Adapters

The frontend API modules for phases 6, 7, 9, and 18 will model the backend response directly. UI-only pagination remains local to components and mock helpers; it is not presented as a server response. Request payloads will match controller requirements exactly.

Phase 7 separates write-form data from response data. The real backend does not return profile form fields, so the frontend must not cast `{elderId, profileVersion}` into a detailed profile. The UI will show available metadata and use explicit local form defaults for editing until the backend contract is expanded through the PDF process.

### Mock-Only Boundary

Each stage exposes an implementation status of `REAL` or `MOCK_ONLY`. Phases 8 and 10-17 remain usable in mock mode but are visibly classified in the integration status model. Real-mode requests do not silently fall back to mock data, because that masks missing endpoints and contract errors.

### Contract Drift Prevention

The backend publishes an OpenAPI document using Springdoc. A committed OpenAPI snapshot is the machine-readable integration artifact. Frontend types for implemented user endpoints are generated from that snapshot with `openapi-typescript`.

Verification has two gates:

1. Backend tests compare the current Springdoc document with the committed snapshot.
2. Frontend `contract:check` regenerates TypeScript definitions and fails when the committed generated file differs.

Any DTO or endpoint change therefore requires an intentional snapshot and generated-type update in the same commit. PDF review remains a human gate before changing the machine-readable contract.

## Error Handling

- Real mode returns backend errors as-is and never substitutes successful mock data.
- Invalid response envelopes fail with the existing frontend format error.
- Missing backend stages report `MOCK_ONLY` instead of `READY`.
- Generated files are not edited manually.

## Verification

- Backend MockMvc tests cover exact response shapes and phase 6 request-body requirements.
- Frontend type checking consumes generated contract types.
- Frontend build verifies component adaptations.
- Contract snapshot and generated-type checks run alongside the existing Maven and pnpm commands.

