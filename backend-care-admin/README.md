# backend-care-admin

Care and admin backend service for CareNest.

This module now only implements member 3 responsibilities for phases 8-18. Existing project files are used as references for contracts, table names, field names, and status values; this module does not take over member 1 database ownership, member 2 user-side backend ownership, or member 4 frontend ownership.

Member 3 scope kept here:

- Phase 8: service item list, detail, create, update.
- Phase 11: admin order list and detail.
- Phase 12: admin dispatch, nurse accept, task status update.
- Phase 13: nurse task workbench list and detail.
- Phase 14: care service records, vital signs, order service record query.
- Phase 15: service report generation and query.
- Phase 17: order cancel/reschedule endpoints assigned to member 3.
- Phase 18: admin demo data status.

Removed because they are not member 3 primary work:

- Family order creation and family order list.
- Elder/family report acknowledgement.
- Family archive suggestion decision.
- Public health endpoint.

Contract rules:

- All endpoints are under `/api/v1`.
- Responses use `{code,message,data,traceId}`.
- Pages use `{records,total,page,size}`.
- Authentication uses `Authorization: Bearer <token>` from the shared `login_session`.
- Database columns stay aligned with existing `db/schema` scripts.
