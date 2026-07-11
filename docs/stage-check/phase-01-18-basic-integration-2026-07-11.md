# Phase 01-18 Basic Integration Check - 2026-07-11

## Scope

This check covers the integrated four-end workflow after the phase 06-18 implementation work: real API routing, database-backed data, order dispatch, care records, service reports, report acknowledgement, and order changes. The frontend runs with mock mode disabled.

## Verification

| Check | Result |
| --- | --- |
| Frontend TypeScript check | `pnpm typecheck` passed |
| User backend package | `mvn -pl backend-user -DskipTests package` passed |
| Care/admin backend package | `mvn -pl backend-care-admin -DskipTests package` passed |
| Elder report list and pending list | Real token returned readable reports and pending reports |
| Family report list and pending list | Real token returned readable reports and pending reports with active binding scope |
| Elder report body | `GET /api/v1/orders/{orderId}/service-report` returned a real report body |
| Report-state consistency repair | Orders and reports in `WAIT_CONFIRM` were verified to be visible to both elder and family users |

## Report Delivery Regression Fixed

Two records had an inconsistent state: the order was `WAIT_CONFIRM` while its report remained `REJECTED`. The user-side pending-report query correctly filters by report state, so those records were not delivered. The generation workflow now resets an existing rejected report to `WAIT_CONFIRM`, refreshes its contents, and clears the prior confirmation timestamp. The two existing inconsistent records were repaired in the local database and verified through both user roles.
