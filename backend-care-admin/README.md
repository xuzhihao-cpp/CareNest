# backend-care-admin

CareNest 护理端与管理端后端服务。

本模块只实现成员3职责。现有项目文件用于对齐接口、表名、字段和状态；不接管成员1数据库、成员2用户侧后端或成员4前端任务。

Member 3 scope kept here:

- Phase 8: service item list, detail, create, update.
- Phase 11: admin order list and detail.
- Phase 12: admin dispatch, nurse accept, task status update.
- Phase 13: nurse task workbench list and detail.
- Phase 14: care service records, vital signs, order service record query.
- Phase 15: service report generation and query.
- Phase 17: order cancel/reschedule endpoints assigned to member 3.
- Phase 18: admin demo data status.
- Phase 21: admin medical file review.
- Phase 23: health archive change suggestions and admin review task list.
- Phase 24: admin health information archive review.
- Phase 25: nurse pre-service health summary.
- Phase 26: nurse qualification submission and admin application list.
- Phase 27: admin nurse qualification review.
- Phase 28: nurse training status and admin training review.
- Phase 29: explainable nurse recommendation.
- Phase 30: preferred nurse selection and recommendation view.

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
- Phase 19, 20 and 22 user-side endpoints are not implemented here; member3 only consumes their data.
- Phase 21-25 use the latest health archive, medical file, suggestion and review-task schemas from `main`.
- Phase 26-30 runtime database verification waits for member1-owned schema migrations; this module does not create those tables.
- Phase 30 preferred nurse selection does not dispatch an order or create a nurse task.
