# Frontend and Backend Contract Workflow

This project uses the member kickoff PDFs as the business contract and the implemented backend OpenAPI document as the current HTTP contract. The frontend must adapt to the backend DTO; it must not add response fields because a screen needs them.

## Update order

1. Update the PDF-backed backend DTO or controller only when the change is approved by the owning member and the database schema permits it.
2. Run the backend contract test and intentionally refresh `contracts/user-api-v1.json` with `-DupdateOpenApiSnapshot=true`.
3. Run `pnpm --dir frontend contract:generate` to regenerate `frontend/src/types/generated/user-api.ts`.
4. Adapt frontend API modules and mock JSON to the generated types.
5. Run the contract check, frontend typecheck/build, and backend tests in the same change.

## Commands

```powershell
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user test
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl backend-user "-DupdateOpenApiSnapshot=true" "-Dtest=UserApiOpenApiContractTest" test
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\pnpm.cmd" --dir frontend contract:check
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\pnpm.cmd" --dir frontend typecheck
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\pnpm.cmd" --dir frontend build:h5
```

## Runtime rules

- Implemented real endpoints are phases 6, 7, 9, 16, and 18 in `backend-user`.
- Phases 8, 10-15, and 17 are `MOCK_ONLY` until a backend controller and contract snapshot exist.
- Real mode must not use `mockFallback: true` for a mock-only stage. A missing endpoint must be visible as an error.
- Mock JSON must have the same `data` shape as the real DTO. Server arrays must not be represented as `PageResult` objects.
- `frontend/src/types/generated/user-api.ts` is generated output and must not be edited manually.

## Ownership of changes

The backend owner changes Java DTOs/controllers and the snapshot. The frontend owner regenerates types and updates adapters/components. A pull request that changes either side must include the generated snapshot/types and pass both contract checks.
