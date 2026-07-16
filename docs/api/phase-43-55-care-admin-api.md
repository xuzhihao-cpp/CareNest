# 成员3阶段43-55护理端与管理端后端 API

## 通用约定

- 服务端口：`${BACKEND_CARE_ADMIN_PORT:8082}`。
- 鉴权头：`Authorization: Bearer <token>`。
- 返回结构沿用全局 `ApiResponse<T>`，业务数据位于 `data`。
- 状态值严格使用成员1冻结的数据字典，不新增数据库状态。

## 阶段43-46 客服、评价投诉与申诉

| 方法 | 路径 | 角色/权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/customer-service/tickets` | 长辈、家属或有客服权限的管理人员 | 创建客服工单 |
| GET | `/api/v1/admin/customer-service/tickets` | `CUSTOMER_SERVICE_TICKET_HANDLE` | 查询客服工单 |
| POST | `/api/v1/admin/customer-service/tickets/{ticketId}/reply` | `CUSTOMER_SERVICE_TICKET_HANDLE` | 回复并推进至 `PROCESSING` |
| POST | `/api/v1/admin/customer-service/tickets/{ticketId}/close` | `CUSTOMER_SERVICE_TICKET_HANDLE` | 关闭工单；紧急工单必须先有回访 |
| POST | `/api/v1/admin/customer-service/tickets/{ticketId}/follow-up` | `FOLLOW_UP_MANAGE` | 添加工单回访 |
| GET | `/api/v1/admin/customer-service/tickets/{ticketId}/follow-ups` | `FOLLOW_UP_MANAGE` | 查询工单回访 |
| POST | `/api/v1/family/orders/{orderId}/reviews` | `FAMILY` | 提交服务评价 |
| POST | `/api/v1/family/orders/{orderId}/complaints` | `FAMILY` | 提交投诉 |
| GET | `/api/v1/admin/complaints` | `COMPLAINT_HANDLE` | 查询投诉 |
| POST | `/api/v1/admin/complaints/{complaintId}/handle` | `COMPLAINT_HANDLE` | 处理或驳回投诉 |
| POST | `/api/v1/nurse/appeals` | `NURSE`；管理场景按目标反查护理员 | 提交护理申诉 |
| GET | `/api/v1/nurse/appeals` | `NURSE` 或有审核权限的管理人员 | 查询本人或全部申诉 |
| POST | `/api/v1/admin/nurse-appeals/{appealId}/review` | `NURSE_APPEAL_REVIEW` | 审核申诉并重算评分 |

状态：工单 `PENDING/PROCESSING/RESOLVED/CLOSED`，投诉 `PENDING/PROCESSING/RESOLVED/REJECTED`，申诉 `PENDING/APPROVED/REJECTED`。

冻结请求没有单独的申诉审核结果字段，因此审核接口用 `targetType` 承载 `APPROVED/REJECTED`，并要求 `targetId` 与原申诉目标一致。评价标签、投诉原因和附件 ID 在不修改成员1表结构的前提下，以结构化 JSON 保存到既有内容字段。

## 阶段47-50 评分与培训文章

| 方法 | 路径 | 角色/权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/admin/nurses/{nurseId}/score/recalculate` | `NURSE_APPEAL_REVIEW` | 按当前指标、投诉和已通过申诉事实重算 |
| GET | `/api/v1/nurses/{nurseId}/score` | 本人或有审核权限的管理人员 | 查询评分 |
| GET | `/api/v1/nurses/{nurseId}/score-logs` | 本人或有审核权限的管理人员 | 查询评分变更记录 |
| GET | `/api/v1/nurse/my-score` | `NURSE`；管理场景允许 `ADMIN` | 护理端评分摘要 |
| GET | `/api/v1/nurse/my-score/change-logs` | `NURSE`；管理场景允许 `ADMIN` | 护理端评分变更分页 |
| GET/POST | `/api/v1/admin/training-articles` | `TRAINING_ARTICLE_MANAGE` | 查询或新建文章 |
| PUT | `/api/v1/admin/training-articles/{articleId}` | `TRAINING_ARTICLE_MANAGE` | 编辑草稿或下线文章 |
| POST | `/api/v1/admin/training-articles/{articleId}/publish` | `TRAINING_ARTICLE_MANAGE` | 发布或下线文章 |
| GET | `/api/v1/nurse/orders/{orderId}/recommended-articles` | 订单护理员；管理场景允许 `ADMIN` | 按服务项目和长辈风险标签推荐 |
| POST | `/api/v1/nurse/articles/{articleId}/read` | 订单护理员；管理场景允许 `ADMIN` | 写入阅读状态 |

评分以 100 分为基准，每次从当前业务事实重新计算并限制在 0-100；分数未变化时不重复写变更日志。文章状态为 `DRAFT/PUBLISHED/OFFLINE`，阅读状态使用 `UNREAD/READ/CONFIRMED`。

## 阶段51-55 随访、看板与演示交付

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/admin/follow-ups` | `FOLLOW_UP_MANAGE` | 创建随访；需要提醒时在同一事务创建提醒任务 |
| GET | `/api/v1/admin/dashboard/basic-statistics` | `DASHBOARD_BASIC_VIEW` | 真实订单、提醒、评价与客服统计 |
| GET | `/api/v1/admin/dashboard/quality-statistics` | `DASHBOARD_QUALITY_VIEW` | 留档、指标、豁免和护理评分统计 |
| POST | `/api/v1/admin/demo-data/reset` | `DEMO_DATA_MANAGE` | 执行成员1维护的 `db/seed/*.sql` |
| GET | `/api/v1/admin/demo-data/status` | `DEMO_DATA_MANAGE` | 返回账号、场景数和最近重置时间 |
| GET | `/api/v1/health` | 公开 | 返回 `status`、`ready` 及既有服务健康字段 |

看板参数为 `dateFrom`、`dateTo`，格式 `yyyy-MM-dd`，闭区间且跨度不得超过 366 天。演示重置资源可通过 `DEMO_DATA_SEED_PATTERN` 配置，默认 `file:./db/seed/*.sql`。
就绪检查固定核验 `elder_demo`、`family_demo`、`nurse_demo`、`admin_demo`、`cs_demo` 五个账号及其角色。

## 明确未实现

- 阶段41-42没有成员3任务。
- 未实现 `/api/v1/elder/assistance-tickets`，该入口属于成员2。
- 未实现独立的长辈提醒创建接口和长辈随访查询接口。
- 未修改 `db/` 数据库契约，也未实现成员4前端页面。
