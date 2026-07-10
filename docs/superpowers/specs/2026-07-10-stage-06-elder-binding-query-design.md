# Stage 6 Elder Binding Query Design

## Goal

Replace the elder-side fabricated pending binding with records read from `elder_family_binding`, so a family-created `PENDING` binding can be viewed and approved by the corresponding elder.

## Contract Extension

- Add `GET /api/v1/elder/bindings` to `backend-user`.
- Require an authenticated user with the `ELDER` role.
- Resolve the elder through `elder_profile.user_id = currentUser.userId`.
- Return every binding for that elder as the existing `BindingResponse[]` DTO.
- Reuse the existing `elder_family_binding`, `elder_profile`, and `authorization_scope` definitions. No database fields or status values are added.

## Data Flow

1. A family member submits `POST /api/v1/family/bindings` with a real elder identifier.
2. The backend inserts an `elder_family_binding` row with `binding_status = PENDING`.
3. The family list and the new elder list read the same database row.
4. The elder approves a selected pending row through `POST /api/v1/elder/bindings/{bindingId}/approve`.
5. Both list endpoints subsequently return the row as `ACTIVE`.

## Frontend

- Use `elder_001`, the database-backed demo elder identifier, as the default invite value.
- Add an elder binding list request to the stage 6 API adapter.
- Load the family list for `FAMILY` and the elder list for `ELDER`.
- Remove the hard-coded `binding-002` fallback.
- Render an approval action beside each real `PENDING` row for the elder role.
- Keep mock mode contract-compatible, but do not synthesize a pending row when none exists.

## Errors And Authorization

- Missing or invalid authentication returns 401 through the existing auth flow.
- A non-elder calling the elder list returns 403.
- An elder can only list bindings associated with their own `elder_profile`.
- Approval continues to verify that the binding belongs to the authenticated elder.

## Verification

- Backend integration test proves create, elder list, approve, and family list use the same binding ID and status transition.
- Backend integration test proves a family account cannot call the elder list.
- OpenAPI snapshot and generated frontend types include the new endpoint.
- Frontend typecheck and H5 build pass.
- Real MySQL verification creates a pending binding, reads it as the elder, approves it, and reads `ACTIVE` from both roles.
