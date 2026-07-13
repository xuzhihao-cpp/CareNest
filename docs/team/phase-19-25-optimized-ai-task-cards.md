# 阶段 19-25 优化版任务卡与前端 AI 提示词

> 依据：`互联网智慧护理平台完整设计文档.docx`、`互联网智慧护理平台正式开工文档第2版增强版.pdf`、阶段 01-18 实际联调问题复盘。
>
> 目的：在不偏离 PDF 的阶段目标、接口路径、数据对象和角色边界的前提下，补上真实数据、权限、状态一致性、可追溯和端到端验收要求。本文件中的“不得使用 mock”指用户可操作的运行环境不得使用 mock 或本地伪数据；单元测试夹具可以存在，但不能成为页面回退数据源。

## 一、PDF 前置固定约定（保留并作为执行基线）

以下内容来自正式开工 PDF 的前置约定。阶段 19-25 的成员或 AI 无权自行更改；若实际仓库已有已验证的兼容性差异，应先记录差异并由项目负责人确认，不能在实现阶段静默改动。

### 1. 本地开发环境

| 项目 | PDF 固定约定 | 当前项目执行说明 |
| --- | --- | --- |
| 操作系统 | Windows 10/11 | 保持 |
| JDK | JDK 17 | 保持 |
| Maven | Maven 3.9.x | 保持 |
| Node.js | Node.js 20 LTS | 保持 |
| 前端包管理 | pnpm | 保持 |
| 后端框架 | Spring Boot + MyBatis Plus | 保持 |
| 数据库 | MySQL 8.0 | 保持 |
| 缓存 | Redis 7 | 保持 |
| 文件存储 | MinIO | 阶段 20 必须真实接入 |
| 容器环境 | Docker Desktop + Docker Compose | 保持 |
| API 调试 | Apifox 或 Postman | 可使用等效真实 HTTP 客户端 |

| 服务 | PDF 端口 | 当前项目约定 |
| --- | --- | --- |
| 前端开发服务 | 3000 | 当前工程 Vite 已验证使用 `5173`；不得在阶段实现中擅自切回或混用，统一以仓库 `vite.config.ts` 为准，并在部署文档记录该兼容性差异。 |
| 用户侧后端 `backend-user` | 8081 | 保持 |
| 护理/管理后端 `backend-care-admin` | 8082 | 保持 |
| MySQL | 3306 | 保持 |
| Redis | 6379 | 保持 |
| MinIO API | 9000 | 保持 |
| MinIO 控制台 | 9001 | 保持 |
| 本地演示 Nginx | 80 | 保持 |

固定环境变量：

```text
APP_ENV=local
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=smart_nursing
MYSQL_USER=smart_nursing
MYSQL_PASSWORD=smart_nursing123
REDIS_HOST=localhost
REDIS_PORT=6379
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET=smart-nursing
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
JWT_SECRET=smart-nursing-local-secret
JWT_EXPIRE_HOURS=24
BACKEND_USER_PORT=8081
BACKEND_CARE_ADMIN_PORT=8082
BACKEND_USER_BASE_URL=http://localhost:8081
BACKEND_CARE_ADMIN_BASE_URL=http://localhost:8082
FRONTEND_API_BASE=/api/v1
```

启动基础依赖：

```powershell
docker compose up -d mysql redis minio
```

### 2. 目录、分支和提交约定

固定目录职责：

```text
smart-nursing-platform/
  backend-user/          # 登录、长辈、家属、绑定、健康档案、提醒、AI、人工协助
  backend-care-admin/    # 护理、管理、订单、派单、指标、客服、评分、文章、看板
  frontend/              # 长辈、家属、护理、管理四端页面
  db/schema/             # 建表 SQL
  db/seed/               # 演示账号与演示数据
  db/migration/          # 版本化结构与数据修复脚本
  docs/api/              # 接口契约
  docs/dictionary/       # 字段与状态字典
  docs/test/             # 测试用例、问题清单
  docs/stage-check/      # 截图、接口响应、数据库记录、验收说明
  mock/                  # 仅保留测试夹具；不可作为真实运行时数据源
  docker/ docker-compose.yml
```

分支规则保留 PDF 原约定：`main` 仅稳定版本；日常集成使用 `develop`（若仓库未建立，应先由负责人建立）；数据库使用 `feature/db-*`，用户侧使用 `feature/user-*`，护理/管理使用 `feature/care-admin-*`，前端使用 `feature/frontend-*`，缺陷修复使用 `fix/*`。本仓库 README 同时要求阶段分支使用 `phase-编号/英文短名`；阶段 19-25 的实际分支命名采用两者兼容形式，例如 `phase-19/health-archive` 或 `feature/frontend-phase-19-health-archive`，并以当前团队既有命名为准。

提交类型：`feat` 新功能、`fix` 缺陷修复、`docs` 文档、`db` 数据库脚本/字典、`test` 测试、`chore` 配置/构建。禁止直接向 `main` 提交业务改动。

### 3. 全局 API 契约

| 规范项 | 固定内容 |
| --- | --- |
| API 前缀 | `/api/v1` |
| 统一返回 | `{ "code": 0, "message": "success", "data": {}, "traceId": "string" }` |
| 分页 | `{ "records": [], "total": 0, "page": 1, "size": 10 }`；前端不得用 `list/items/rows` 替代 `records` |
| 认证 | `Authorization: Bearer <token>` |
| 时间 | ISO 8601，例如 `2026-07-08T10:00:00+08:00` |
| 文件上传 | `POST /api/v1/files`，`multipart/form-data`，返回 `fileId`、`url`、`originalName`、`mimeType`、`size`、`auditStatus` |
| 错误码 | `0` 成功；`400` 参数错误；`401` 未登录；`403` 无权限；`404` 不存在；`409` 状态冲突；`422` 业务规则不满足；`500` 服务异常 |

接口归属：`backend-user:8081` 负责 `/auth`、`/elder`、`/elders`、`/family`、`/reminders`、`/ai`、`/assistance`；`backend-care-admin:8082` 负责 `/admin`、`/nurse`、`/nurses`、`/orders`、`/service-items`、`/dashboard`、`/customer-service`。前端只能按契约调用接口，不得直接访问数据库或自行拼接未冻结路径。

### 4. 数据字典、角色与核心状态

标识字段保持 camelCase：`elderId`、`familyId`、`nurseId`、`serviceId`、`orderId`、`fileId`、`reportId`、`metricItemId`、`ticketId`；数据库列使用 snake_case；接口字段使用 camelCase。

| 类别 | 固定值 |
| --- | --- |
| 角色 `roleCode` | `ELDER`、`FAMILY`、`NURSE`、`ADMIN`、`CUSTOMER_SERVICE` |
| 绑定 `bindingStatus` | `PENDING`、`ACTIVE`、`REJECTED`、`REVOKED` |
| 授权 `scopeCode` | `HEALTH_VIEW`、`HEALTH_EDIT`、`ORDER_CREATE`、`REPORT_VIEW`、`REPORT_CONFIRM`、`ARCHIVE_EDIT` |
| 订单 `orderStatus` | `WAIT_DISPATCH`、`DISPATCHED`、`ACCEPTED`、`ON_THE_WAY`、`SERVING`、`WAIT_REPORT`、`WAIT_CONFIRM`、`COMPLETED`、`CANCELED` |
| 审核 `auditStatus` | `PENDING`、`APPROVED`、`REJECTED`、`NEED_MORE` |
| 提醒 `reminderStatus` | `PENDING`、`DONE`、`SNOOZED`、`MISSED`、`NEED_HELP` |
| 指标 `metricStatus` | `PENDING`、`SUBMITTED`、`PASS`、`MISSING`、`PENDING_PROOF`、`EXEMPT_APPROVED`、`EXEMPT_REJECTED` |

阶段 19-25 需新增的任何状态必须先写入 `docs/dictionary/data-dictionary.md`，并与上表保持兼容；优先复用 `NEED_MORE`，不再另建 `NEEDS_SUPPLEMENT` 同义状态。

### 5. 数据库、演示账号和日志约定

基础表包括：账号权限 `sys_user`、`sys_role`、`user_role`、`sys_permission`、`role_permission`、`login_session`、`operation_log`；用户基础 `elder_profile`、`family_profile`、`nurse_profile`、`elder_family_binding`、`authorization_scope`；业务基础 `service_item`、`service_address`、`nursing_order`、`order_status_log`、`nurse_task`、`file_asset`。

通用字段：`id`、`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted`。状态变更必须记录 `source_status`、`target_status`、`operator_id`、`operate_time`、`remark` 或等价的既有日志字段。

固定演示账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 长辈 | `elder_demo` | `Demo@123456` |
| 家属 | `family_demo` | `Demo@123456` |
| 护理 | `nurse_demo` | `Demo@123456` |
| 管理员 | `admin_demo` | `Demo@123456` |
| 客服 | `cs_demo` | `Demo@123456` |

每轮端到端测试应在可识别的测试数据集或重置脚本上运行，不能依赖长期累积的演示脏数据。任何正式数据修复应落入可审查 migration 或明确记录的修复脚本，不能只在数据库控制台手改后不留痕。

### 6. PDF mock 约定的本轮修订

PDF 原文要求前后端 mock 与真实接口字段一致。结合阶段 01-18 的实际问题，阶段 19-25 采用以下更严格规则：契约 mock 只能用于离线单元测试或后端尚未可用时的独立开发验证；真实模式页面不得自动回退 mock，不得显示 mock 测试入口，不得把 mock 成功结果作为验收证据。所有阶段验收必须包含真实接口与真实数据库结果。

## 附录 A：阶段 01-25 全量接口契约基线

本附录将 PDF 的全局接口总契约中阶段 01-25 的内容整理为可执行版本，并纳入已确认的真实联调修订。阶段 19-25 的任何 AI 均须先阅读本附录；接口路径、字段、角色、状态和表归属以此为唯一指导。与旧 PDF 不同但已由真实实现验证的内容，在“已确认修订”中明确列出。

### A.1 路由归属与调用规则

| 路由前缀 | 服务 | 端口 | 规则 |
| --- | --- | --- | --- |
| `/api/v1/auth`、`/api/v1/elder`、`/api/v1/elders`、`/api/v1/family`、`/api/v1/reminders`、`/api/v1/ai`、`/api/v1/assistance` | `backend-user` | 8081 | 用户、绑定、档案、报告确认、健康反馈、文件登记等用户侧能力 |
| `/api/v1/admin`、`/api/v1/nurse`、`/api/v1/orders`、`/api/v1/service-items`、`/api/v1/dashboard`、`/api/v1/customer-service` | `backend-care-admin` | 8082 | 服务项目、订单、派单、护理、报告、审核、管理能力 |
| `/api/v1/files` | 以最终冻结的文件服务路由为准 | 由后端代理 | 阶段 20 的真实 multipart 上传，禁止直接在浏览器暴露 MinIO 密钥 |

前端请求必须通过 `src/api` 的统一请求层；Vite 代理只负责本地开发转发，不改变 API 路径。所有写操作在刷新后必须重新读取真实后端结果。

### A.2 阶段 01-05：基础与认证

| 阶段 | 方法与路径 | 请求 | 成功 `data` | 角色/规则 | 数据对象 |
| --- | --- | --- | --- | --- | --- |
| 01 | `GET /api/v1/health` | 无 | `{status,appName,version,serverTime}` | 公开或登录态 | 应用健康状态 |
| 01 | `GET /api/v1/version` | 无 | `{status,appName,version,serverTime}` | 公开或登录态 | 应用版本 |
| 02 | `POST /api/v1/auth/login` | `{username,password}` | `{token,userId,displayName,roles,menus}` | 公开 | `sys_user`、`sys_role`、`user_role`、`login_session` |
| 02 | `POST /api/v1/auth/logout` | 无 | 登录态失效结果 | 已登录用户 | `login_session` |
| 02 | `GET /api/v1/auth/me` | 无 | 当前用户与角色 | 已登录用户 | 账号/角色 |
| 02 | `GET /api/v1/auth/menus` | 无 | 当前角色菜单 | 已登录用户 | 角色/菜单 |
| 03 | `GET /api/v1/auth/permissions` | 无或契约查询参数 | `{roleCode,permissions}` | 已登录用户 | `sys_permission`、`role_permission` |
| 03 | `POST /api/v1/admin/roles/{roleId}/permissions` | `{permissionCodes}` | `{roleCode,permissions}` | `ADMIN` 或 `CUSTOMER_SERVICE` + permissionCode | 权限表、`operation_log` |
| 04 | `GET /api/v1/elder/home-summary` | 无 | `{cards,quickActions,todoCount}` | `ELDER`；家属访问须授权 | 长辈首页摘要 |
| 04 | `GET /api/v1/family/home-summary` | 无 | `{cards,quickActions,todoCount}` | `FAMILY + ACTIVE + scope` | 家属首页摘要 |
| 04 | `GET /api/v1/nurse/workbench-summary` | 无 | `{cards,quickActions,todoCount}` | `NURSE`；管理审阅按权限 | 护理工作台摘要 |
| 04 | `GET /api/v1/admin/dashboard/overview` | 无 | `{cards,quickActions,todoCount}` | 管理/客服 + permissionCode | 管理首页摘要 |
| 05 | 前端交付：`request(method,url,data)`、`ApiResponse<T>`、`PageResult<T>`、`FileUploadResult` | - | 统一封装 | 不允许组件自行请求或运行时 mock 回退 | `frontend/src/api`、`frontend/src/types` |

### A.3 阶段 06-09：绑定、基础档案、服务和地址

| 阶段 | 方法与路径 | 请求 | 成功 `data` | 角色/规则 | 数据对象 |
| --- | --- | --- | --- | --- | --- |
| 06 | `POST /api/v1/family/bindings` | `{elderInviteCode,relationType,scopeCodes}` | `{bindingId,elderId,elderName,relationType,bindingStatus,scopeCodes}` | 家属发起；后续需长辈确认；同一家属与长辈已有 `PENDING/ACTIVE` 绑定时返回 `409`，前端应改用更新接口 | `elder_family_binding`、`authorization_scope`、`operation_log` |
| 06 | `GET /api/v1/family/bindings` | 无 | 绑定记录列表 | 家属本人 | 同上 |
| 06 | `POST /api/v1/elder/bindings/{bindingId}/approve` | 无或确认信息 | 更新后的绑定记录 | 绑定对应长辈本人 | 同上 |
| 06 | `PUT /api/v1/family/bindings/{bindingId}/scopes` | `{scopeCodes}` | 更新后的绑定；生效绑定返回 `scopeUpdatePending=true` 与 `pendingScopeCodes` | `FAMILY + PENDING/ACTIVE`；ACTIVE 范围变更须长辈再次确认，确认前原 `scopeCodes` 保持不变 | 同上 |
| 06 | `POST /api/v1/family/bindings/{bindingId}/revoke` | 无或撤销说明 | 更新后的绑定记录 | 有归属的家属 | 同上 |
| 07 | `GET /api/v1/elders/{elderId}/profile` | 无 | 基础档案与 `profileVersion` | 本人或有授权家属 | `elder_profile`、`elder_contact`、`health_archive_change_log` |
| 07 | `PUT /api/v1/elders/{elderId}/profile` | `{name,gender,birthDate,careLevel,emergencyContacts}` | `{elderId,profileVersion}` | `FAMILY + ACTIVE + HEALTH_EDIT/ARCHIVE_EDIT` 或既定本人范围 | 同上 |
| 07 | `GET /api/v1/family/elders` | 无 | 当前家属有权管理的长辈列表 | `FAMILY + ACTIVE` | 绑定与长辈档案 |
| 08 | `GET /api/v1/service-items` | 查询参数可按契约扩展 | 服务项目列表 | 已登录用户 | `service_item` |
| 08 | `GET /api/v1/service-items/{serviceId}` | 无 | 服务项目详情 | 已登录用户 | `service_item` |
| 08 | `POST /api/v1/admin/service-items` | `{serviceName,category,price,durationMinutes,status}` | `{serviceId,serviceName,price,durationMinutes,status}` | 管理/客服 + permissionCode | `service_item`、`operation_log` |
| 08 | `PUT /api/v1/admin/service-items/{serviceId}` | 同创建请求 | 服务项目详情 | 管理/客服 + permissionCode | `service_item`、`operation_log` |
| 08 | `DELETE /api/v1/admin/service-items/{serviceId}` | 无 | 删除结果 | 管理/客服 + permissionCode；被订单引用时返回业务冲突 | `service_item`、订单关联 |
| 09 | `GET /api/v1/elders/{elderId}/service-addresses` | 无 | 地址列表 | 本人或授权家属 | `service_address` |
| 09 | `POST /api/v1/elders/{elderId}/service-addresses` | `{contactName,contactPhone,regionCode,detailAddress,isDefault}` | `{addressId,fullAddress,isDefault}`；`fullAddress` 固定为 `regionCode + 单个空格 + detailAddress`，例如 `310101 人民路200号2单元301` | 授权家属 | `service_address` |
| 09 | `PUT /api/v1/service-addresses/{addressId}` | 同地址请求 | 地址详情 | 地址归属校验 | `service_address` |
| 09 | `DELETE /api/v1/service-addresses/{addressId}` | 无 | 删除结果 | 地址归属校验；历史订单引用时保护地址快照并返回业务提示 | `service_address`、订单地址快照 |

### A.4 阶段 10-14：订单、派单与护理执行

| 阶段 | 方法与路径 | 请求 | 成功 `data` | 角色/规则 | 数据对象 |
| --- | --- | --- | --- | --- | --- |
| 10 | `POST /api/v1/family/orders` | `{elderId,serviceId,addressId,scheduledStart,preferredNurseId,remark}` | `{orderId,orderNo,orderStatus}` | `FAMILY + ACTIVE + ORDER_CREATE`；服务已上架、地址归属有效 | `nursing_order`、`order_status_log`、地址快照 |
| 10 | `GET /api/v1/family/orders` | 分页/筛选按契约 | `{records,total,page,size}` 或冻结的订单列表结构 | 当前家属有权订单 | 订单、状态日志 |
| 10 | `GET /api/v1/orders/{orderId}` | 无 | 订单详情 | 订单关联家属、长辈、护理、管理按资源归属 | 订单、状态日志 |
| 11 | `GET /api/v1/admin/orders` | `{page,size,orderStatus,keyword,dateFrom,dateTo}` | `{records,total,page,size}` | 管理/客服 + permissionCode | `nursing_order`、`order_status_log`、`service_item`、`elder_profile` |
| 11 | `GET /api/v1/admin/orders/{orderId}` | 无 | 订单详情与状态日志 | 管理/客服 + permissionCode | 同上 |
| 12 | `POST /api/v1/admin/orders/{orderId}/dispatch` | `{nurseId,dispatchRemark}` | `{orderId,orderStatus,taskId}` | 管理/客服 + permissionCode；只能从 `WAIT_DISPATCH` 派单 | `nursing_order`、`nurse_task`、`order_status_log` |
| 12 | `POST /api/v1/nurse/tasks/{taskId}/accept` | 无或既定确认数据 | `{orderId,orderStatus,taskId}` | 被派护理本人；状态不可跳跃 | 同上 |
| 12 | `POST /api/v1/nurse/tasks/{taskId}/status` | `{targetStatus}` | `{orderId,orderStatus,taskId}` | 被派护理本人；顺序为接单、出发、服务中、结束服务 | 同上 |
| 13 | `GET /api/v1/nurse/tasks` | `{status,page,size}` | `{records,total,page,size}` | 护理仅看本人任务；管理按权限审阅 | `nurse_task`、`nursing_order` |
| 13 | `GET /api/v1/nurse/tasks/{taskId}` | 无 | 任务详情 | 被派护理本人或管理审阅 | 同上 |
| 14 | `POST /api/v1/nurse/orders/{orderId}/service-records` | `{startTime,endTime,content,nursingAdvice,abnormalFlag}` | `{recordId,orderStatus}` | 被派护理本人；结束时间晚于开始时间 | `care_service_record`、`vital_sign_record` |
| 14 | `POST /api/v1/nurse/orders/{orderId}/vital-signs` | `{startTime,endTime,content,nursingAdvice,abnormalFlag}` | `{recordId,orderStatus}` | 被派护理本人 | 同上 |
| 14 | `GET /api/v1/orders/{orderId}/service-records` | 无 | 已保存服务/生命体征记录 | 按订单资源归属和 scope | 同上 |

### A.5 阶段 15-18：报告、确认、变更与全链路

| 阶段 | 方法与路径 | 请求 | 成功 `data` | 角色/规则 | 数据对象 |
| --- | --- | --- | --- | --- | --- |
| 15 | `POST /api/v1/orders/{orderId}/service-report/generate` | `{orderId}` 或无 body | `{reportId,summary,vitalSigns,serviceRecords,nursingAdvice}` | 管理或被派护理；必须已有真实服务记录 | `service_report`、`service_report_item` |
| 15 | `GET /api/v1/orders/{orderId}/service-report` | 无 | `{reportId,summary,vitalSigns,serviceRecords,nursingAdvice}` | 长辈本人、授权家属、关联护理、管理按资源/授权读取 | 同上 |
| 16 | `GET /api/v1/elder/reports` | 无 | `[{reportId,orderId,elderId,elderName}]` | 长辈本人 | `service_report`、订单、长辈档案 |
| 16 | `GET /api/v1/elder/reports/pending` | 无 | `[{reportId,orderId,elderId,elderName}]` | 长辈本人；仅 `WAIT_CONFIRM` 报告 | 同上 |
| 16 | `GET /api/v1/family/reports` | 无 | `[{reportId,orderId,elderId,elderName}]` | `FAMILY + ACTIVE + REPORT_CONFIRM`；前端必须按每条报告的 `elderId` 匹配绑定，不得合并不同长辈的 scope | 报告、订单、绑定 |
| 16 | `GET /api/v1/family/reports/pending` | 无 | `[{reportId,orderId,elderId,elderName}]` | `FAMILY + ACTIVE + REPORT_CONFIRM`；前端必须按每条报告的 `elderId` 匹配绑定 | 同上 |
| 16 | `POST /api/v1/elder/reports/{reportId}/ack` | `{ackResult,satisfaction,remark,acceptedSuggestionIds}` | `{ackId,ackResult,reportStatus}` | 长辈本人 | `care_report_ack`、`health_info_review_task`、报告/订单状态 |
| 16 | `POST /api/v1/family/reports/{reportId}/ack` | 同上 | `{ackId,ackResult,reportStatus}` | `FAMILY + ACTIVE + REPORT_CONFIRM` | 同上 |
| 16 | `POST /api/v1/family/reports/{reportId}/archive-suggestions/decision` | 同上 | `{ackId,ackResult,reportStatus}` | `FAMILY + ACTIVE + REPORT_CONFIRM + ARCHIVE_EDIT` | 同上 |
| 17 | `POST /api/v1/family/orders/{orderId}/cancel` | `{reason}` | `{orderId,orderStatus,scheduledStart}` | `FAMILY + ACTIVE + ORDER_CREATE`；`SERVING` 及之后不可普通取消 | `nursing_order`、`order_status_log` |
| 17 | `POST /api/v1/family/orders/{orderId}/reschedule` | `{reason,newScheduledStart}` | `{orderId,orderStatus,scheduledStart}` | 同上；时间合法且状态允许 | 同上 |
| 17 | `POST /api/v1/admin/orders/{orderId}/cancel` | `{reason}` | `{orderId,orderStatus,scheduledStart}` | 管理/客服 + permissionCode | 同上 |
| 18 | `GET /api/v1/health` | 无 | 健康服务信息 | 公开或登录态 | 服务状态 |
| 18 | `GET /api/v1/admin/demo-data/status` | 无 | `{ready,accounts,scenarioCount}` | 管理/客服 + permissionCode；生产页面不展示系统检查入口 | `operation_log`、验收数据 |

已确认修订：`satisfaction` 的合法范围为 `0..100`；报告重新生成时必须同步报告和订单的 `WAIT_CONFIRM` 状态；用户报告列表是阶段 16 的真实已批准扩展，禁止移除或用静态列表替换。

### A.6 阶段 19-25：健康信息增强与服务前摘要

| 阶段 | 方法与路径 | 请求 | 成功 `data` | 角色/规则 | 数据对象 |
| --- | --- | --- | --- | --- | --- |
| 19 | `GET /api/v1/elders/{elderId}/health-archive` | 无 | 归档版本及 `diseases`、`medications`、`allergies`、`riskTags`、`carePlan` | 长辈本人或有 `HEALTH_VIEW` 的 ACTIVE 家属；护理端经阶段 25 只读摘要 | `health_archive`、`chronic_disease`、`medication_plan`、`allergy_record`、`risk_tag`、`care_plan` |
| 19 | `PUT /api/v1/elders/{elderId}/health-archive` | `{diseases,medications,allergies,riskTags,carePlan}` 加并发版本 | `{archiveVersion}` | `FAMILY + ACTIVE + HEALTH_EDIT`；关键变更留痕 | 同上、变更日志 |
| 19 | `POST /api/v1/elders/{elderId}/medications` | 冻结后的用药 DTO | `{archiveVersion}` | 同上；不做自动诊断/改药 | `medication_plan`、日志 |
| 20 | `POST /api/v1/files` | `multipart/form-data` 文件 | `{fileId,url,originalName,mimeType,size,auditStatus}` | 当前登录用户、文件大小/类型/归属校验 | `file_asset`、对象存储 |
| 20 | `POST /api/v1/elders/{elderId}/medical-files` | `{fileId,fileType,title,occurredAt}` | `{medicalFileId,fileId,auditStatus}` | `FAMILY + ACTIVE + HEALTH_EDIT` | `medical_file`、`file_asset` |
| 20 | `GET /api/v1/elders/{elderId}/medical-files` | 分页/筛选按冻结契约 | 病历资料列表 | 本人或授权家属；护理仅可在阶段 25 看批准资料 | 同上 |
| 21 | `GET /api/v1/admin/medical-files` | 分页、状态和筛选参数 | `{records,total,page,size}` | 管理/客服 + permissionCode | `medical_file`、审核任务、日志 |
| 21 | `GET /api/v1/admin/medical-files/{fileId}` | 无 | 病历详情与授权预览信息 | 同上 | 同上 |
| 21 | `POST /api/v1/admin/medical-files/{fileId}/review` | `{auditStatus,reviewComment,extractToArchive,extractedItems}` | `{fileId,auditStatus,reviewedAt}` | 同上；驳回/补充意见必填 | `medical_file`、`health_info_review_task`、`operation_log` |
| 22 | `POST /api/v1/elder/health-feedback` | `{feedbackType,severity,content,inputType,fileId}` | `{feedbackId,createdAt}` | 长辈本人；不接受任意 elderId | `elder_health_feedback`、`voice_command_log` |
| 22 | `GET /api/v1/family/elders/{elderId}/health-feedback` | 分页/筛选按冻结契约 | 反馈记录列表 | `FAMILY + ACTIVE + HEALTH_VIEW` | 同上 |
| 23 | `POST /api/v1/orders/{orderId}/health-update-suggestions` | `{fieldName,newValue,sourceType,sourceId,reason}` | `{suggestionId,status}` | 关联护理/有权提交方；来源与订单必须真实 | `health_update_suggestion`、`health_info_review_task` |
| 23 | `GET /api/v1/admin/health-review-tasks` | 分页/状态/来源筛选 | `{records,total,page,size}` | 管理/客服 + permissionCode | 建议、审核任务 |
| 24 | `GET /api/v1/admin/health-review-tasks/{taskId}` | 无 | 任务、来源、当前值、建议值和证据 | 管理/客服 + permissionCode | 审核任务、归档、日志 |
| 24 | `POST /api/v1/admin/health-review-tasks/{taskId}/archive` | `{decisions:[{sourceField,targetField,normalizedValue,decision,comment}]}` | `{taskId,status,archiveVersion}` | 管理/客服 + permissionCode；事务和并发保护 | `health_info_review_task`、`health_archive`、`health_archive_change_log` |
| 24 | `GET /api/v1/elders/{elderId}/health-archive/change-logs` | 分页/筛选按冻结契约 | 变更历史列表 | 本人或授权家属 | 归档变更日志 |
| 25 | `GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary` | 无 | `{elderProfile,riskTags,medications,diseases,allergies,approvedMedicalFiles,recentReports}` | 被派护理本人；管理仅审阅；其他用户 `403` | `health_archive`、`medical_file`、`service_report` |

### A.7 跨阶段强制状态机与可见性规则

1. 阶段 19 家属编辑档案只产生归档版本和日志；护理/报告来源的变更必须走阶段 23，再由阶段 24 归档。
2. 阶段 20 病历上传初始为 `PENDING`；阶段 21 `APPROVED` 后才可被阶段 25 的护理摘要读取。
3. 阶段 14 服务记录完成后进入报告生成链；阶段 15 报告生成前必须存在真实服务记录；阶段 16 确认只处理 `WAIT_CONFIRM` 报告。
4. 阶段 23 建议创建后，正式 `health_archive` 不变；阶段 24 批准决策完成后才更新归档并新增变更日志。
5. 阶段 25 只读取已归档健康信息、审核通过病历和有权限的近期报告。任务切换时前端必须清空上一个长辈数据。
6. 任何重试/重新生成必须同步检查订单、报告、审核任务和日志状态。不得仅改一张表导致管理端和用户端看到不同业务状态。

## 二、19-25 阶段总规则

### 1. 开始前必须完成的检查

1. 阅读项目 `README.md`、本文件、`docs/dictionary/data-dictionary.md`、现有 API 文档和对应阶段验收记录。
2. 查看 `git status`、当前分支、已有数据库迁移和现有后端 Controller/Service；不得覆盖或回滚其他成员的改动。
3. 先确认真实后端接口、MySQL、文件存储服务是否可用。接口缺失时，不得在页面伪造成功结果，不得以 mock 代替。
4. 新增或调整字段、枚举、接口、状态前，先更新数据字典、API 契约、SQL migration 和阶段验收计划；不能只改一个端。

### 2. 全阶段不可违反的实现规则

- 所有接口统一返回 `{ code, message, data, traceId }`；页面只显示业务化提示，不显示 DTO、API 路径、traceId、内部 ID 或 mock 文案。
- 任何写操作必须在真实数据库事务中完成，并写入 `operation_log` 或对应的状态日志。跨表状态变化必须在同一事务完成。
- 权限判断必须同时包含：登录角色、资源归属、绑定是否 `ACTIVE`、授权范围 scope、订单/任务归属。只判断角色不视为完成。
- 页面不得依赖“选中后才出现的数据”作为唯一数据源。首次进入页面必须加载真实列表、默认选中合理业务对象，并处理空、无权限、加载失败和刷新状态。
- 订单、报告、审核任务、档案版本等关联对象必须做一致性检查；任何流程重新提交、驳回后重试、重新生成时，明确规定每个关联状态如何回退或重置。
- 移动端为长辈、家属、护理端；管理端为桌面网页。表单用合适控件约束数据，日期/时间用选择器，电话和数值输入限制格式。
- 必须用真实角色数据验证至少一次：允许访问、资源不属于当前用户、绑定失效、scope 缺失、重复提交、网络失败/服务异常、刷新后数据保持一致。
- 集成测试必须事务回滚或使用独立测试数据。若种子数据已存在相同家属与长辈的 `PENDING/ACTIVE` 绑定，创建测试应先在当前测试事务中清理该关系或改用独立账号；不得把正确的 `409` 规则改成允许重复绑定。

### 3. 建议的状态命名和字典治理

PDF 已要求状态来自固定字典。新增阶段开始前由数据库/字典负责人确认并冻结以下状态名称；后端、前端、SQL、测试不能各自写另一套字符串：

| 领域 | 建议状态 | 说明 |
| --- | --- | --- |
| 病历资料 | `PENDING`、`APPROVED`、`REJECTED`、`NEED_MORE` | 对应待审核、通过、驳回、需补充，复用 PDF 审核状态字典 |
| 健康建议 | `PENDING`、`APPROVED`、`REJECTED`、`ARCHIVED` | 建议不能直接覆盖档案；复用既有字典后再实施 |
| 审核任务 | `PENDING`、`IN_REVIEW`、`ARCHIVED`、`REJECTED` | 任务生命周期需与建议状态可追溯对应 |
| 健康反馈严重度 | `LOW`、`MEDIUM`、`HIGH` | 仅用于风险提示，不构成医疗诊断 |

若现有字典已有等价状态，优先复用现有字典，不重命名、不并行造词。

### 4. 每一阶段的统一交付物

- 版本化 SQL：`db/migration/`，含升级与可验证查询；不使用手工临时 SQL 作为正式交付。
- 后端：DTO、Controller、Service、Mapper/Repository、权限校验、事务、单元/集成测试。
- 前端：真实 API adapter、类型、页面、加载/空/失败/无权限状态、中文业务文案和响应式布局。
- 文档：`docs/api/`、`docs/dictionary/`、`docs/stage-check/phase-xx-*.md`。
- 证据：真实接口响应、数据库查询结果、四端截图、权限 403 截图/响应、自动化测试结果。

## 三、19-25 前置子阶段

> 这两个子阶段属于“阶段 19-25 健康信息增强”计划，不占用或替换 PDF 的主阶段 19-25。命名为 `19-A`、`19-B`，必须在阶段 19 健康档案增强开始前通过。它们的职责是提供真实、可复现的运行环境和一致性基础，而不是新增业务功能或改写既有 API 契约。

---

## 子阶段 19-A：Docker 部署与可复现真实运行环境

| 字段 | 内容 |
| --- | --- |
| 阶段组 | 阶段 19-25 前置基础设施 |
| 模块 | Docker、Docker Compose、Nginx、环境变量、四端演示环境 |
| 主责建议 | 成员 1 / 成员 3 / 成员 4，项目负责人统筹验收 |
| 前置依赖 | 阶段 18 已具备真实主流程；现有 MySQL、Redis、MinIO Compose 基础服务 |
| 后续依赖 | 19-B、阶段 19-25 的真实联调与验收 |
| 开发目标 | 一次 Compose 启动 MySQL、Redis、MinIO、用户后端、护理/管理后端和前端 Nginx；四端通过同一入口访问真实接口。 |
| 测试完成检验 | 关闭本机开发进程后，仅执行文档命令即可启动全栈；四个演示账号可真实登录并完成一条跨端链路。 |

### 19-A.1 必须实现的内容

1. 保留根目录 `docker-compose.yml` 的 MySQL、Redis、MinIO 定义、数据卷、初始化 SQL 与健康检查，不破坏已有本地联调。
2. 新增 `backend-user` 和 `backend-care-admin` 的多阶段 Dockerfile：Maven + JDK 17 构建，JRE 17 运行，非 root 用户运行；镜像中不保留源码、Maven 缓存、`.env` 或任何真实密钥。
3. 新增前端 H5 构建镜像：Node 20 + pnpm 构建，Nginx 运行。前端只能使用 `/api/v1` 相对路径，不得把 `localhost:8081`、`localhost:8082` 编译进发布产物。
4. 新增应用 Compose 编排（可为 `docker-compose.app.yml` 或兼容 profile）：`backend-user`、`backend-care-admin`、`frontend` 加入与 `mysql`、`redis`、`minio` 相同网络。
5. Nginx 代理规则：
   - `/` 提供前端 H5；
   - 用户侧 `/api/v1/auth`、`/api/v1/elder`、`/api/v1/elders`、`/api/v1/family` 等按冻结路由代理到 `backend-user`；
   - 护理/管理路由代理到 `backend-care-admin`；
   - Bearer Token、multipart 上传、HTTP 状态码与错误响应必须透传，不能被静态 HTML 或 200 覆盖。
6. 后端依赖 MySQL、Redis、MinIO 的健康检查后再启动；前端依赖两个后端的 `/api/v1/health` 通过。不得只依赖 `depends_on` 的启动顺序。
7. 配置 `utf8mb4`、容器时区和 SQL 初始化编码，确保中文 seed、页面、日志和病历元数据不乱码。
8. 提供 `docker/env/.env.example`、`.gitignore` 规则、启动/停止/日志/重置/回滚命令和部署说明。

### 19-A.2 文件、环境与接口边界

必须新增或完善以下文件（可按现有目录微调，但职责不得缺失）：

```text
docker/
  backend-user.Dockerfile
  backend-care-admin.Dockerfile
  frontend.Dockerfile
  nginx/default.conf
  env/.env.example
  README.md
docker-compose.app.yml
docs/deployment/phase-19a-docker-local.md
docs/stage-check/phase-19a-docker.md
```

`.env.example` 至少包含 `MYSQL_*`、`REDIS_*`、`MINIO_*`、`JWT_*`、`BACKEND_USER_PORT`、`BACKEND_CARE_ADMIN_PORT`。真实 `.env` 不提交，示例值使用 `change-me`，不得把 PDF 中的示例密码原样当作生产密钥。

不新增业务 API，不改变 `/api/v1`、统一返回、分页、JWT、角色或状态字典。文件上传接口 `POST /api/v1/files` 必须在 Nginx 下真实工作，`client_max_body_size` 以阶段 20 的文件契约为准。

### 19-A.3 验收门禁

1. `docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d --build` 后全部服务健康。
2. 仅通过 Nginx 入口完成四端加载、登录、鉴权 API、服务报告读取和文件上传；不得访问前端开发服务器或直接后端端口作为验收替代。
3. 停止任一后端容器时，页面显示真实失败而非旧数据或假成功；恢复后刷新恢复真实数据。
4. 删除并重建应用容器后，MySQL、Redis、MinIO 数据卷仍存在；只有执行文档化重置命令才删除演示数据。
5. 使用 `elder_demo`、`family_demo`、`nurse_demo`、`admin_demo` 至少跑通“下单 - 派单 - 护理记录 - 报告 - 确认”链路。
6. 保存容器状态、镜像构建日志、真实接口响应、数据库验证、四端截图和回滚步骤到 `docs/stage-check`。

### 19-A.4 可直接给 AI 的任务输入

```text
实现子阶段 19-A：Docker 部署与可复现真实运行环境。

工作目录：项目根目录（包含 README.md、frontend/、backend-user/、backend-care-admin/）
先阅读 README.md、docs/team/phase-19-25-optimized-ai-task-cards.md、现有 docker-compose.yml、docker/README.md、两个后端配置与 vite.config.ts；查看 git status，不覆盖他人改动，不改 main，不自动提交或推送。

目标：容器化 backend-user、backend-care-admin 和前端 H5/Nginx，并与现有 mysql、redis、minio 组成一次 Compose 可启动的真实四端环境。不得改变业务 API/DTO/状态/角色；不得使用 mock、localStorage 伪数据或静态成功页面。

必须实现多阶段 Dockerfile、应用 Compose、Nginx 路由代理、.env.example、非 root 运行、健康检查、依赖就绪等待、UTF-8/utf8mb4、数据卷保留、上传代理、错误状态透传、启动/停止/重置/回滚文档。前端只访问 /api/v1 相对路径，Nginx 将用户侧路由转发 8081、护理/管理路由转发 8082，并保留 Authorization 和 multipart。

完成后真实执行 Docker build/up，用 Nginx 入口验证四端登录、真实 API、上传和至少一条跨端订单到报告确认链路。将命令、容器状态、接口响应、数据库记录、失败恢复和回滚步骤写入 docs/deployment/phase-19a-docker-local.md 与 docs/stage-check/phase-19a-docker.md。出现阻塞时报告具体缺口，不得用假实现掩盖。
```

---

## 子阶段 19-B：Redis 缓存、会话辅助与并发保护

| 字段 | 内容 |
| --- | --- |
| 阶段组 | 阶段 19-25 前置基础设施 |
| 模块 | Redis 连接、缓存失效、短锁、会话辅助、故障降级 |
| 主责建议 | 成员 2 / 成员 3 / 成员 4，成员 1 配合一致性验证 |
| 前置依赖 | 19-A 的 Redis 容器和全栈真实环境 |
| 后续依赖 | 阶段 19-25 的档案、病历、审核、服务前摘要联调 |
| 开发目标 | 使用 Redis 7 提升热点只读访问和重复提交保护，但 MySQL 始终是业务事实来源。 |
| 测试完成检验 | 可验证缓存命中、写后失效、Redis 故障安全回源、并发写入最多成功一次，且不泄露跨用户数据。 |

### 19-B.1 必须实现的内容

1. 在 `backend-user` 与 `backend-care-admin` 增加 Redis 统一配置、统一缓存服务和统一短锁服务，使用 `REDIS_HOST`、`REDIS_PORT` 环境变量、合理连接超时和安全 JSON 序列化。
2. MySQL 是订单、报告、绑定、档案和审核任务的唯一事实来源；Redis 只能存可重新计算的缓存、短锁和可失效辅助数据。Redis 不可用不能造成越权、丢写或假成功。
3. 第一批缓存范围：
   - 上架服务项目：`carenest:service-items:on-shelf:v1`，TTL 5 分钟；服务项目新增、编辑、上下架、删除后立即失效。
   - 四端首页摘要：`carenest:home:{role}:{userHash}:v1`，TTL 30 秒；订单、任务、绑定、报告、反馈、授权变化后按关联用户失效。
   - 服务前健康摘要默认不缓存完整敏感正文；确有性能需求时必须使用短 TTL、`userHash + orderId + archiveVersion` 组合键，并在档案、病历、报告变化后失效。
4. 对订单状态切换、派单、报告确认/重新生成、档案归档实现短锁：如 `carenest:lock:order:{orderId}`、`carenest:lock:report:{reportId}`、`carenest:lock:archive:{taskId}`。锁不可得返回既有 `409`，并继续由数据库条件更新、版本号或事务作为最终裁决。
5. 缓存读取顺序必须为“认证/资源归属/scope 校验 -> Redis -> MySQL 回源”；禁止先读缓存再判断用户权限。
6. 写操作在数据库事务成功提交后再失效缓存。事务回滚不得写入缓存或产生“已更新”缓存。
7. 不在 Redis key/value 中存明文密码、JWT、手机号、完整病历正文、文件存储密钥。用户相关 key 使用不可逆 `userHash`；日志不输出 Redis value。
8. 不在本子阶段偷偷将现有 JWT 认证改为 Redis 唯一登录态。token 撤销、刷新令牌、限流如需新增，必须另行冻结 API 与字典。

### 19-B.2 键、TTL 与失效矩阵

| 场景 | key | TTL | 失效触发 | 安全/一致性要求 |
| --- | --- | --- | --- | --- |
| 上架服务项目 | `carenest:service-items:on-shelf:v1` | 5 分钟 | 服务项目任何写操作 | 不含敏感个人数据 |
| 长辈首页 | `carenest:home:ELDER:{userHash}:v1` | 30 秒 | 提醒、订单、报告、反馈、绑定变化 | 不含其他用户数据 |
| 家属首页 | `carenest:home:FAMILY:{userHash}:v1` | 30 秒 | 绑定、订单、报告、反馈、授权变化 | 先校验 ACTIVE 与 scope |
| 护理首页 | `carenest:home:NURSE:{userHash}:v1` | 30 秒 | 派单、接单、任务、服务记录变化 | 不缓存其他护理人员任务 |
| 管理首页 | `carenest:home:ADMIN:{userHash}:v1` | 30 秒 | 订单、任务、审核任务变化 | 先校验 permissionCode |
| 订单锁 | `carenest:lock:order:{orderId}` | 10-30 秒 | 派单、接单、改期、取消、状态更新 | 数据库条件更新兜底 |
| 报告锁 | `carenest:lock:report:{reportId}` | 10-30 秒 | 报告确认与重新生成 | 防重复确认，数据库事务兜底 |
| 审核锁 | `carenest:lock:archive:{taskId}` | 10-30 秒 | 健康归档 | 与档案版本/行锁共同使用 |

所有新增 Redis key 必须在 `docs/deployment/phase-19b-redis.md` 记录拥有者、TTL、失效事件、敏感性和 Redis 故障行为。

### 19-B.3 前端边界

- 不增加任何 Redis 专属页面、按钮、卡片或技术文字。
- 写成功后仍重新读取真实接口；前端不得假定“缓存已经刷新”。
- `409` 显示“数据刚刚被其他操作更新，请刷新后重试”；网络/500 失败不能把旧缓存显示成最新结果。
- 仅按业务结果验收：服务项目修改后列表立即正确、重复派单/确认不产生重复记录、切换角色不会看见其他人的首页摘要。

### 19-B.4 验收门禁

1. 首次读服务项目/首页走 MySQL 并回填，二次访问有可验证缓存命中证据，字段和权限结果不变。
2. 管理端上下架服务后，家属端刷新立刻反映正确列表，不等待 TTL。
3. 绑定撤销或 scope 改动后，档案、报告和首页不会从旧缓存泄露内容。
4. 并发确认同一报告、派同一订单或归档同一任务时，最多一个成功；其余为 `409` 或既定幂等结果，数据库与日志没有重复记录。
5. 暂停 Redis 容器后，只读请求安全回源 MySQL；高风险写操作仍由数据库一致性保护；恢复 Redis 后缓存可重建。
6. 扫描 Redis 键和值，确认没有明文密码、JWT、手机号、完整病历或 MinIO 密钥。
7. 保存缓存命中/失效、Redis 故障、并发冲突、权限失效、数据库记录和前端截图作为验收证据。

### 19-B.5 可直接给 AI 的任务输入

```text
实现子阶段 19-B：Redis 缓存、会话辅助与并发保护。

工作目录：项目根目录（包含 README.md、frontend/、backend-user/、backend-care-admin/）
先阅读 README.md、docs/team/phase-19-25-optimized-ai-task-cards.md、两个后端配置、docker-compose.yml、认证/订单/报告/档案服务；查看 git status，不覆盖他人改动，不改 main，不自动提交或推送。

目标：在 backend-user 与 backend-care-admin 真实接入 Redis 7，用于可失效缓存和短时并发锁；MySQL 是唯一业务事实来源。不要重新设计 JWT、角色、API 路径、DTO、状态或前端路由，也不要用 mock 回退。

必须实现统一 Redis 配置、缓存服务、短锁服务；缓存上架服务项目和四端首页摘要；写事务成功后再失效；Redis 故障时读安全回源 MySQL。对订单状态切换、派单、报告确认/重新生成、档案归档使用短锁 + 数据库条件更新/版本校验双重保护，并发重复操作最多成功一次。不得缓存明文密码、JWT、手机号、完整病历、MinIO 密钥；用户 key 使用 hash，所有 key 使用 carenest: 前缀和版本。

前端不显示 Redis 技术内容，只保证业务刷新、409、500、权限失效正确。完成后真实启动 Redis，验证缓存命中、写后失效、绑定/scope 失效不泄露旧缓存、Redis 停止后的安全回源、并发报告确认/订单派单不重复写入。补齐配置、键策略、部署文档、后端测试、真实 API/数据库证据和 docs/stage-check/phase-19b-redis.md。出现阻塞时报告具体缺口，不得用假实现掩盖。
```

### 19-A/19-B 完成后的统一门禁

1. 19-A 的容器化环境是阶段 19-25 的正式验收环境；后续阶段不得只在本机散进程通过就标记完成。
2. 19-B 的缓存失效矩阵必须随阶段 19-25 新增写操作同步更新，不能在最后统一补救。
3. 两个子阶段完成后，以 Docker 环境重新执行阶段 18 主链路与阶段 19-25 的“真实写入 - 数据库检查 - 下一角色读取 - 越权 403 - 刷新一致性”门禁。

## 四、阶段详细任务卡

---

## 阶段 19：健康档案增强

### 目标与边界

扩展长辈健康档案的慢病、用药、过敏、风险标签和照护计划。家属保存后，长辈端和护理端的摘要必须读取同一份已保存数据。该阶段只建立和维护档案，不把护理端输入直接写入档案。

### PDF 基线

- PDF 前置依赖：阶段 18。
- 优化准入门禁：子阶段 19-A 与 19-B 已通过；阶段 19 的真实档案数据必须在容器化环境和 Redis 一致性规则下验收。
- 数据对象：`health_archive`、`chronic_disease`、`medication_plan`、`allergy_record`、`risk_tag`、`care_plan`。
- 接口：
  - `GET /api/v1/elders/{elderId}/health-archive`
  - `PUT /api/v1/elders/{elderId}/health-archive`
  - `POST /api/v1/elders/{elderId}/medications`
- 写入请求核心字段：`diseases`、`medications`、`allergies`、`riskTags`、`carePlan`。
- 写入响应核心字段：`archiveVersion`。

### 后端任务

1. 先盘点阶段 7 基础档案、阶段 16 审核任务和既有 `health_archive_change_log`，确定复用关系；禁止另建重复的“健康档案主表”。
2. 设计并冻结健康档案读取 DTO：读取接口必须返回足以渲染摘要和编辑页的归档版本及上述五类数据；写入接口仅返回 `archiveVersion` 和必要状态。子项字段先写入数据字典，再实现。
3. 权限实现：
   - 长辈仅能读取本人档案；不允许直接修改家属维护的关键健康字段。
   - 家属必须同时满足 `FAMILY`、与该长辈的 `ACTIVE` 绑定和 `HEALTH_EDIT` 才能写；读取至少要求 `HEALTH_VIEW`。
   - 护理端不直接调用编辑接口，阶段 25 只读健康摘要。
4. 使用版本号实现并发保护：写入请求应带当前版本或等价并发令牌；旧版本提交返回 `409`，页面提示“档案已被更新，请刷新后再保存”。
5. 每次写入采用事务：更新归档主数据和子表、增加版本、写入来源 `FAMILY_EDIT` 的变更日志和操作日志。不得先删后写导致保存失败时档案为空。
6. 用药新增接口必须校验药物名称、频次/时间点和重复项；任何药物信息只做记录，不做诊断、剂量推荐或自动改药。

### 前端任务

- 家属端做“健康档案”页面：以分段表单呈现慢病、用药、过敏、风险标签、照护计划；编辑与阅读分离，保存前显示本次将修改的内容。
- 长辈端只展示大字号、低认知负担的健康摘要：风险提示、当前用药、照护要点；不得暴露复杂编辑表单或内部版本号。
- 护理端不在阶段 19 单独造一套档案页，摘要入口留给阶段 25 并复用同一 API/类型。
- 标签为可选择/可删除的业务标签，不以逗号输入内部编码。用药建议使用重复项提醒、日期选择器和固定频次选项。
- 保存成功后立即重新读取后端数据；不能只更新本地表单状态。

### 必测场景与验收

1. 家属 A 保存档案后，刷新家属端、长辈端、护理摘要接口，三处内容一致。
2. 家属 B 无绑定、绑定失效、缺 `HEALTH_EDIT` 分别返回 `403`。
3. 两个浏览器以同一旧版本保存，后一笔返回 `409`，旧页面不覆盖新数据。
4. 数据库可追溯到归档版本和来源日志；保存失败不会产生半套子表数据。

### AI 可直接执行的任务输入

```text
实现阶段 19：健康档案增强。

工作目录：项目根目录（包含 README.md、frontend/、backend-user/、backend-care-admin/）
先阅读 README.md、docs/team/phase-19-25-optimized-ai-task-cards.md、docs/dictionary/data-dictionary.md、docs/api/、阶段 7 和阶段 16 的现有代码与验收文档。不要覆盖现有未提交改动。

目标：使用真实后端、真实 MySQL 实现慢病、用药、过敏、风险标签和照护计划。接口路径固定为 GET/PUT /api/v1/elders/{elderId}/health-archive 和 POST /api/v1/elders/{elderId}/medications。不要新增替代路径，不要在运行时使用 mock、localStorage 伪数据或前端假成功。

必须实现：ACTIVE 绑定 + HEALTH_VIEW/HEALTH_EDIT + 资源归属权限；归档版本并发冲突返回 409；事务性写入、operation_log 和健康档案变更日志；家属保存后长辈端和护理端摘要从真实接口同步读取。

交付：SQL migration、字典/API 文档、后端测试、真实 API 验证、前端页面和 docs/stage-check/phase-19-*.md。运行 typecheck、相关 Maven 测试和真实角色 API 测试。页面不可显示 ID、API、DTO、traceId 或 mock 字样。
```

---

## 阶段 20：病历资料上传

### 目标与边界

支持家属上传处方、检查报告、出院小结和既往病历，写入真实文件存储和数据库。未审核资料可以显示“待审核”，但绝不能自动写入健康档案或护理摘要。

### PDF 基线

- 前置依赖：阶段 19。
- 数据对象：`file_asset`、`medical_file`。
- 接口：
  - `POST /api/v1/files`
  - `POST /api/v1/elders/{elderId}/medical-files`
  - `GET /api/v1/elders/{elderId}/medical-files`
- 关联请求字段：`fileId`、`fileType`、`title`、`occurredAt`。
- 响应：`medicalFileId`、`fileId`、`auditStatus`。

### 后端任务

1. 将文件上传与病历登记分为两个真实步骤：先以 multipart 上传文件到已配置对象存储，再以 `fileId` 绑定病历元数据。上传接口返回的文件信息要进入 `file_asset`，不得仅返回临时 URL。
2. 在 API 契约中冻结允许的 MIME 类型、最大尺寸、下载/预览授权、文件名处理规则；拒绝空文件、类型伪装、超限文件和不属于当前用户的 `fileId`。
3. 家属登记病历要求 `ACTIVE + HEALTH_EDIT`；长辈可查看本人已授权可见资料；管理端阶段 21 可审；护理端阶段 25 只读审核通过资料。
4. 病历首次登记状态为 `PENDING_REVIEW`，记录上传人、上传时间、发生日期、文件类型、标题和对象存储引用；写入操作日志。
5. 任何数据库登记失败应删除/标记孤立文件，或记录可重试清理任务；不得留下用户无法管理的孤儿文件。

### 前端任务

- 家属端增加“病历资料”分区，先选择业务类别，再选择文件、填写标题和发生日期，显示上传进度、成功/失败和审核状态。
- 文件列表按业务类型与审核状态显示，使用可读名称、上传日期、审核结果和处理意见；不把 `fileId`/对象存储 key 展示给用户。
- 预览和下载必须使用真实授权 URL；失败时提示重新尝试或联系平台，不能用本地缩略图假装上传成功。
- 删除/替换行为先与后端契约明确；未定义前不在前端添加无效按钮。

### 必测场景与验收

1. 真实上传后对象存储有文件、`file_asset` 与 `medical_file` 有关联数据、状态为待审核。
2. 上传超限、错误类型、断网、登记失败均有明确业务提示且不会生成假记录。
3. 非授权家属、其他长辈、护理人员访问未批准资料返回 `403`。
4. 家属端刷新后仍能看到真实审核状态。

### AI 可直接执行的任务输入

```text
实现阶段 20：病历资料上传。

在既有 CareNest 工程中实现 POST /api/v1/files、POST/GET /api/v1/elders/{elderId}/medical-files。先检查现有 MinIO/文件服务配置和阶段 19 权限实现。必须真实上传并保存 file_asset 与 medical_file；不能用 base64、localStorage、静态假文件或 mock 回退。

实现 ACTIVE + HEALTH_EDIT 的家属上传权限、文件归属校验、类型/大小校验、PENDING_REVIEW 初始状态和 operation_log。未审核资料不得进入健康档案或护理摘要。前端家属端应提供类型选择、文件选择、标题、发生日期、进度、失败重试和审核状态；只显示业务名称，不显示 fileId、存储 key、API 或 traceId。

交付真实对象存储验证、数据库查询、403/422/500 测试、接口/字典/迁移/阶段验收文档。不要自行虚构删除、分享或下载接口；缺失契约必须先记录并报告。
```

---

## 阶段 21：管理端病历审核

### 目标与边界

管理端审核病历资料，支持通过、驳回、要求补充。审核结果必须回显给家属；审核通过可以创建后续健康信息审核任务，但不能绕过阶段 24 的正式归档流程。

### PDF 基线

- 前置依赖：阶段 20。
- 数据对象：`medical_file`、`health_info_review_task`、`operation_log`。
- 接口：
  - `GET /api/v1/admin/medical-files`
  - `GET /api/v1/admin/medical-files/{fileId}`
  - `POST /api/v1/admin/medical-files/{fileId}/review`
- 审核请求：`auditStatus`、`reviewComment`、`extractToArchive`、`extractedItems`。
- 响应：`fileId`、`auditStatus`、`reviewedAt`。

### 后端任务

1. 管理列表必须支持真实分页、审核状态、文件类型、长辈和日期筛选；默认不一次性读取所有文件。
2. 详情接口只提供经权限控制的临时预览/下载信息和病历元数据；所有管理员访问都写操作日志。
3. 审核行为必须事务化：
   - `APPROVED`：允许根据 `extractToArchive` 创建 `health_info_review_task`，但不直接修改 `health_archive`。
   - `REJECTED`、`NEEDS_SUPPLEMENT`：`reviewComment` 必填，保存审核人和审核时间。
   - 已审资料重复审核需根据字典决定 `409` 或允许重新审核；规则必须写入契约和测试。
4. 权限：`ADMIN` 或 `CUSTOMER_SERVICE` 且具备对应 permissionCode；其他角色和无权限管理员返回 `403`。

### 前端任务

- 管理端桌面页采用“左侧可筛选列表 + 右侧病历详情/预览 + 审核操作区”的工作流，适合连续审核，不做巨型空白表单。
- 审核动作使用状态选择、审核意见文本区和“提取为档案建议”开关；驳回/要求补充时即时标记意见必填。
- 审核成功后刷新列表、详情和家属可见状态；不显示文件内部编号。
- 不将“通过”描述为“已写入健康档案”，应清楚写为“已进入档案审核流程”。

### 必测场景与验收

1. 家属上传的待审病历在管理端可查、可预览、可审核，审核结果刷新后回显给家属。
2. 驳回/需补充缺少意见返回 `422`；重复审核按既定规则返回正确结果。
3. `extractToArchive=true` 后数据库出现审核任务，但健康档案未被直接覆盖。
4. 非管理员、无 permissionCode、跨角色预览均返回 `403`。

### AI 可直接执行的任务输入

```text
实现阶段 21：管理端病历审核。以阶段 20 的真实 medical_file 数据为唯一输入，禁止初始化前端假文件列表。

固定接口：GET /api/v1/admin/medical-files、GET /api/v1/admin/medical-files/{fileId}、POST /api/v1/admin/medical-files/{fileId}/review。实现 ADMIN/CUSTOMER_SERVICE + permissionCode 校验、真实分页筛选、文件详情授权预览、通过/驳回/要求补充审核和 operation_log。驳回或要求补充必须有审核意见；通过且 extractToArchive 时仅创建 health_info_review_task，不直接写 health_archive。

管理端必须是桌面工作台：列表、筛选、详情、审核操作合理排布，审核后真实刷新。前端不显示 fileId、API、DTO、traceId；不做 mock、无效快捷按钮或假成功。补齐迁移/字典/API/测试/验收证据。
```

---

## 阶段 22：长辈健康反馈

### 目标与边界

长辈用大按钮、文字或语音附件反馈疼痛、头晕、睡眠、饮食、精神状态。高严重度只触发风险提示和家属可见记录，不进行自动诊断、改药或虚假的“已通知客服”承诺。

### PDF 基线

- 前置依赖：阶段 18。
- 数据对象：`elder_health_feedback`、`voice_command_log`。
- 接口：
  - `POST /api/v1/elder/health-feedback`
  - `GET /api/v1/family/elders/{elderId}/health-feedback`
- 请求：`feedbackType`、`severity`、`content`、`inputType`、`fileId`。
- 响应：`feedbackId`、`createdAt`。

### 后端任务

1. 只有长辈本人可创建自己的反馈；不得接受前端传入的任意 elderId 覆盖当前身份。
2. `inputType` 区分按钮、文字、语音；语音 `fileId` 必须来自本人已授权的文件上传记录。所有输入统一转为结构化反馈记录，并保留语音意图/附件引用日志。
3. 高严重度反馈写入操作日志，并让有 `HEALTH_VIEW` 的 ACTIVE 家属在真实反馈列表中优先可见。客服工单/推送的正式建模留给对应后续阶段，当前不能伪造“已推送”。
4. 家属读取必须校验 `FAMILY + ACTIVE + HEALTH_VIEW + elderId 归属`；无权限返回 `403`。
5. 内容长度、反馈类型、严重度和文件归属均应参数校验；不允许把反馈直接写为诊断结论或健康档案字段。

### 前端任务

- 长辈端做适老化页面：疼痛、头晕、睡眠、饮食、精神状态使用大尺寸图标/文字按钮；严重程度使用清晰的三级选择；支持补充文字和上传/录制语音附件。
- 提交后显示“已记录，将供授权家属和护理人员参考”，不显示内部记录 ID。
- 高严重度采用清晰但不恐慌的警示区，提供“联系家属/平台协助”的现有入口；不得显示医疗诊断建议。
- 家属端按时间线显示反馈，突出高严重度、反馈类型、描述和语音附件入口；只有授权后才加载。

### 必测场景与验收

1. 长辈提交每种反馈类型后，家属端刷新可见；数据库可查到反馈与输入来源。
2. 无绑定、非 ACTIVE、缺 `HEALTH_VIEW` 家属读取返回 `403`。
3. 高严重度只出现风险提示和记录优先级，不自动修改档案、不自动生成诊断。
4. 语音附件不属于当前长辈或上传失败时不可提交。

### AI 可直接执行的任务输入

```text
实现阶段 22：长辈健康反馈。固定接口为 POST /api/v1/elder/health-feedback 和 GET /api/v1/family/elders/{elderId}/health-feedback，使用真实数据库，不用 mock。

长辈端必须为适老化移动页面：大按钮反馈疼痛、头晕、睡眠、饮食、精神状态；可选严重度、补充文字、真实语音附件。不要让用户输入 elderId、feedbackId 或英文枚举。后端从登录身份确定长辈本人，家属读取必须同时校验 FAMILY、ACTIVE 绑定、HEALTH_VIEW scope 和资源归属。

高严重度只做风险提示、操作留痕和家属列表优先展示；不可自动诊断、改药或假称已通知客服。完成真实 API/DB/403/附件归属/跨端刷新测试和阶段验收文档。
```

---

## 阶段 23：健康档案变更建议

### 目标与边界

护理记录或服务报告可以产生健康档案字段变更建议，进入审核队列。建议绝不能直接覆盖正式健康档案；阶段 24 才能归档。

### PDF 基线

- 前置依赖：阶段 15、19。
- 数据对象：`health_update_suggestion`、`health_info_review_task`。
- 接口：
  - `POST /api/v1/orders/{orderId}/health-update-suggestions`
  - `GET /api/v1/admin/health-review-tasks`
- 请求：`fieldName`、`newValue`、`sourceType`、`sourceId`、`reason`。
- 响应：`suggestionId`、`status`。

### 后端任务

1. 复用现有阶段 16 `health_info_review_task`，先梳理其状态和外键，避免再造一套同义审核任务。
2. 只允许与订单有真实关系的护理人员、服务报告生成方或有明确业务权限的管理员提交建议；不能接受匿名请求或任意 `sourceId`。
3. 校验 `fieldName` 是否为阶段 19 已定义、允许归档的字段；`newValue` 必须按对应字段规范化。禁止前端传任意 SQL/JSON 片段或覆盖整份档案。
4. 创建建议和审核任务必须在同一事务完成，保存订单、来源、提交人、原因和初始状态；写入操作日志。
5. 建议重复提交需有幂等规则：同订单、同来源、同字段、同值的未处理建议不能无限重复；返回既有任务或 `409`，规则写入契约。

### 前端任务

- 护理端在保存服务记录/生成报告后提供“建议更新健康档案”入口，不把它塞进报告确认页面。
- 表单仅展示允许建议的字段及其符合档案类型的控件；用户选择业务字段后输入新值和原因，不能手输内部字段名或 sourceId。
- 提交后显示“已提交管理端审核，未立即修改档案”，并展示当前处理状态。
- 管理端列表显示待审建议来源、长辈、订单服务、建议字段、原值/建议值、原因和提交时间，不显示技术 ID 为主标题。

### 必测场景与验收

1. 护理人员提交建议后，档案原值不变，管理端可看到对应审核任务。
2. 非该订单护理人员、无权限家属、伪造 sourceId 均返回 `403` 或 `422`。
3. 同一建议重复提交按契约正确幂等，不生成重复待审任务。
4. 数据库可追溯建议、任务、订单、来源和操作日志。

### AI 可直接执行的任务输入

```text
实现阶段 23：健康档案变更建议。固定接口为 POST /api/v1/orders/{orderId}/health-update-suggestions 和 GET /api/v1/admin/health-review-tasks。先审查阶段 16 已有 health_info_review_task，必须复用或兼容，不得创建平行审核体系。

护理/报告产生的建议只能进入 health_update_suggestion 和 health_info_review_task，绝不能直接更新 health_archive。校验订单归属、来源真实性、允许字段、值格式和重复提交幂等性；写入 operation_log。前端采用业务字段选择和相应控件，绝不暴露 fieldName、sourceId、orderId 等内部输入；提交后清楚说明“等待管理端审核”。

完成真实数据库联调：原档案不变、管理端看得到待审任务、越权返回 403、重复提交符合规则。补齐字典/API/迁移/验收证据，不要引入 mock 回退。
```

---

## 阶段 24：健康信息审核归档

### 目标与边界

管理端审核健康信息任务，将经确认的标准化字段写入正式健康档案，并记录来源、审核人、旧值、新值和归档版本。归档是唯一能改变正式健康档案的审核入口。

### PDF 基线

- 前置依赖：阶段 21、23。
- 数据对象：`health_info_review_task`、`health_archive`、`health_archive_change_log`。
- 接口：
  - `GET /api/v1/admin/health-review-tasks/{taskId}`
  - `POST /api/v1/admin/health-review-tasks/{taskId}/archive`
  - `GET /api/v1/elders/{elderId}/health-archive/change-logs`
- 归档请求：`decisions: [{ sourceField, targetField, normalizedValue, decision, comment }]`。
- 响应：`taskId`、`status`、`archiveVersion`。

### 后端任务

1. 任务详情必须返回审核所需的来源证据、当前档案值、建议值、字段规范和历史变更摘要；敏感附件预览继续走授权链。
2. 归档接口只允许 `ADMIN` 或 `CUSTOMER_SERVICE` 且具备 permissionCode。采用数据库事务和任务行锁/版本校验，避免两个审核员同时归档同一任务。
3. 对每个 decision 校验源字段、目标字段和可写字段映射。`APPROVE/ARCHIVE` 更新归档，`REJECT` 保留原档案，`NEEDS_SUPPLEMENT` 不改变归档；具体枚举先写入字典。
4. 每一项真正变化必须写 `health_archive_change_log`，包括来源类型/ID、审核人、旧值、新值、审核意见和归档版本。无变化也需说明“无实际差异”的审核结果，不能伪造变更日志。
5. 归档完成后更新任务状态、建议状态和 `archiveVersion`；家属/长辈查询变更日志时继续遵守归属和 scope。

### 前端任务

- 管理端做桌面审核工作台：左侧任务列表，中央来源与当前档案对比，右侧逐字段处理决定；不使用一长串内部 JSON 文本框。
- 每个字段清楚展示“当前值、建议值、规范化后值、处理决定、审核说明”。对需要补充或驳回的决定强制填写说明。
- 归档后页面实时更新任务状态、档案版本和变更日志摘要；不能只显示“保存成功”。
- 长辈/家属端在健康档案页可查看可读的变更历史，隐藏审核员内部 ID 和技术字段。

### 必测场景与验收

1. 管理员批准一项建议后，正式档案更新、版本递增、变更日志完整；刷新三个端的数据一致。
2. 驳回、要求补充不改档案，但任务和原因可回显。
3. 双管理员同时提交：一人成功，另一人收到 `409` 并刷新任务。
4. 无 permissionCode、无绑定家属、跨长辈读取变更日志均返回 `403`。

### AI 可直接执行的任务输入

```text
实现阶段 24：健康信息审核归档。固定接口 GET /api/v1/admin/health-review-tasks/{taskId}、POST /api/v1/admin/health-review-tasks/{taskId}/archive、GET /api/v1/elders/{elderId}/health-archive/change-logs。

这是唯一可更新正式 health_archive 的审核入口。实现 ADMIN/CUSTOMER_SERVICE + permissionCode 校验、事务和并发冲突保护；按逐字段 decisions 归档，写 health_archive_change_log、operation_log、archiveVersion。驳回或要求补充不得修改档案。必须复用阶段 19 字段定义、阶段 21/23 审核任务和建议来源，不得另起字段或状态体系。

前端管理端做桌面逐字段对比审核工作台；用户端只显示可读的变更历史。禁止内部 JSON 输入、ID 主导显示、假成功和 mock。完成真实多角色、并发 409、刷新一致性、DB 日志和验收文档验证。
```

---

## 阶段 25：服务前健康摘要

### 目标与边界

护理人员在开始服务前查看经过审核、与本订单长辈相关的健康摘要、风险、用药、过敏、病历摘要和近期服务报告。该页面只读，重点减少护理风险，不提供档案编辑。

### PDF 基线

- 前置依赖：阶段 19、24。
- 数据对象：`health_archive`、`medical_file`、`service_report`。
- 接口：`GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary`。
- 响应：`elderProfile`、`riskTags`、`medications`、`diseases`、`allergies`、`approvedMedicalFiles`、`recentReports`。
- 只返回审核通过或授权可见资料。

### 后端任务

1. 校验护理人员确实被派至该订单；允许管理端仅作审阅用途读取，其他护理人员、家属和长辈一律 `403`。
2. 仅在护理任务开始前或服务执行允许的状态返回摘要；是否允许服务完成后回看须写入契约，不能隐式放开。
3. 读取阶段 19 已归档健康数据、阶段 20/21 已审核通过病历资料、阶段 15 已生成近期报告。未审核病历和待审核建议不得进入摘要。
4. 返回 DTO 只包含护理执行必要信息，不泄露家属联系方式、内部审核意见、文件存储路径或不相干病历。
5. 记录护理人员查看摘要的操作日志；接口必须保证同一订单多次读取得到一致的当前归档版本信息。

### 前端任务

- 护理端移动工作台在“开始服务”前增加摘要入口；点击后加载真实数据，加载完成前不得显示旧订单数据。
- 布局顺序：高风险提示、过敏、当前用药、慢病和照护要点、审核通过病历摘要、近期服务摘要。风险信息用醒目但克制的颜色和清晰文字。
- 页面只读；病历资料提供授权预览入口，近期报告只显示与本次护理相关摘要。无资料时用清晰空状态，不编造数据。
- 返回任务后保留当前订单上下文；任务切换时清除前一位长辈的摘要，防止隐私串页。

### 必测场景与验收

1. 被派单护理人员在开始服务前可看到风险、用药、病历摘要和近期服务；页面刷新后仍来自真实接口。
2. 未派单护理人员、家属、长辈访问返回 `403`；护理端切换任务不会残留上一位长辈资料。
3. 未审核病历、待审建议不出现在摘要；阶段 24 归档后刷新可见新档案版本。
4. 数据库/日志可追溯摘要访问记录，页面不显示技术 ID 或存储路径。

### AI 可直接执行的任务输入

```text
实现阶段 25：服务前健康摘要。固定接口为 GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary，响应仅包含 elderProfile、riskTags、medications、diseases、allergies、approvedMedicalFiles、recentReports。

必须使用阶段 19 和 24 的真实归档数据、阶段 20/21 已通过病历和阶段 15 报告。护理人员必须实际被派至该订单；ADMIN 仅可审阅；其他护理人员、家属、长辈返回 403。不得泄露未审核资料、存储路径、内部审核意见或其他长辈数据。

护理端为移动端只读服务前页面：先风险、过敏、用药，再慢病/照护要点、病历摘要、近期服务。任务切换必须清除上一个订单的数据；空状态不能伪造内容；不显示 ID、API、DTO、traceId、mock。完成真实角色访问、跨订单隐私、审核后刷新、DB 日志和验收证据测试。
```

## 五、前端实现 AI 主提示词

以下提示词用于把阶段 19-25 的前端任务交给独立 AI。每次只执行一个阶段；在提示词末尾替换阶段编号和阶段专项输入。它吸收了阶段 01-18 的问题：不再允许 mock 掩盖后端缺失，不再显示技术调试信息，不再仅做组件静态演示。

```text
你是 CareNest 智慧护理平台的前端实施与真实接口联调工程师。当前工作目录为项目根目录（包含 README.md、frontend/、backend-user/、backend-care-admin/）。

本次只实现【阶段 XX：阶段名称】的前端部分，并与现有真实后端、MySQL、文件服务联调。先读取：
1. README.md；
2. docs/team/phase-19-25-optimized-ai-task-cards.md；
3. docs/dictionary/data-dictionary.md；
4. docs/api/ 中相关契约；
5. 对应已有阶段组件、API adapter、类型、路由和后端 Controller/DTO；
6. git status，绝不回滚或覆盖其他成员的改动。

强制规则：
- 不使用 mock、localStorage、静态数组、伪造成功响应或测试按钮作为用户页面数据来源。真实接口不存在或 DTO 不足时，先定位并报告具体后端缺口；不得用前端假实现掩盖。
- 保持现有 uni-app + Vue + TypeScript 架构、请求封装和目录风格。新增接口必须进入 src/api，新增类型进入 src/types；不得在组件里硬编码接口路径或伪造 DTO。
- 管理端是桌面网页；长辈、家属、护理端是移动端。页面使用业务化中文，不显示 API、DTO、traceId、数据库主键、内部枚举、mock、测试、演示账号等技术文本。
- 所有表单使用适合的输入限制：日期/时间用选择器，数值设置范围，电话/文件/标签等按业务规则限制；提交前和后端失败后均有可理解的提示。
- 所有页面实现加载、空数据、403 无权限、422 校验失败、409 冲突和网络/500 失败状态；空状态不可伪造业务数据。
- 实现完必须运行 pnpm typecheck；在真实开发服务器下验证至少一个允许角色和一个越权角色。涉及跨端数据时，验证写入后刷新另一端能够看到真实变化。
- 使用 apply_patch 编辑文件。不要进行无关重构、不要修改 main、不要自动提交或推送。

完成时输出：改动文件、真实接口调用链、权限规则、已执行验证、未解决的后端契约缺口。并在 docs/stage-check/ 增加阶段验收记录，记录真实请求响应摘要、数据库验证项和截图路径。

【阶段专项输入粘贴在这里】
```

## 六、前端阶段专项提示词

将下列内容替换主提示词最后的“阶段专项输入”。

### 阶段 19 前端专项

```text
实现家属端健康档案增强、长辈端摘要同步和护理端摘要数据预留。家属页面分为慢病、用药、过敏、风险标签、照护计划，保存时使用真实 GET/PUT /api/v1/elders/{elderId}/health-archive 和 POST /api/v1/elders/{elderId}/medications。默认加载当前选择长辈的真实档案；保存成功后重新 GET，不接受本地“已保存”替代。处理 HEALTH_VIEW/HEALTH_EDIT、ACTIVE 绑定、409 档案版本冲突。长辈只读且适老化，不显示版本号。将阶段 25 所需健康摘要类型设计为可复用的 API 类型，但不要提前造页面或假数据。
```

### 阶段 20 前端专项

```text
实现家属端病历资料上传与列表。流程必须为真实文件上传取得 fileId，再真实登记 medical file；页面显示文件类别、标题、发生日期、上传进度和审核状态。使用文件类型选择、日期选择器、真实失败重试；不要显示 fileId、对象存储 key 或临时 URL。未审核资料显示“待审核”，不能显示为“已归档”。页面刷新后从 GET /api/v1/elders/{elderId}/medical-files 读取。验证上传成功、类型/尺寸校验失败、上传中断、无授权家属 403。
```

### 阶段 21 前端专项

```text
实现管理端桌面病历审核工作台。使用真实 GET /api/v1/admin/medical-files、详情接口和 review 接口。布局为筛选/分页列表、详情预览、审核区；审核区支持通过、驳回、要求补充和“进入档案审核”选项。驳回与要求补充时强制审核意见。审核成功后刷新列表和详情，并让家属端真实状态可回显。不要做内部 ID 输入框、接口快照卡、mock 快捷按钮或无效按钮。
```

### 阶段 22 前端专项

```text
实现长辈端健康反馈和家属端反馈查看。长辈端为大按钮、少输入、适老化移动页：疼痛、头晕、睡眠、饮食、精神状态；选择严重程度、可补充文字和真实语音附件。提交使用 POST /api/v1/elder/health-feedback，不让用户选择 elderId。家属端以真实时间线读取 GET /api/v1/family/elders/{elderId}/health-feedback，突出高严重度但不提供医疗诊断。处理 HEALTH_VIEW/ACTIVE 绑定无权限，所有技术字段隐藏。
```

### 阶段 23 前端专项

```text
实现护理端“提交健康档案变更建议”入口和管理端待审建议列表。护理端从当前真实订单上下文发起 POST /api/v1/orders/{orderId}/health-update-suggestions，表单使用业务字段选择与相应输入控件，用户不得输入 fieldName/sourceId/orderId。提交后明确显示“等待管理端审核，档案尚未修改”。管理端用真实 GET /api/v1/admin/health-review-tasks 显示长辈、服务、来源、当前值、建议值、原因和状态。不得把建议提交直接改到健康档案，也不得把阶段 16 报告确认 UI 复制为本阶段页面。
```

### 阶段 24 前端专项

```text
实现管理端健康信息审核归档工作台和用户端可读变更记录。管理端真实读取任务详情，逐字段显示当前值、建议值、规范化值、处理决定和审核说明，并调用 archive 接口。禁止内部 JSON 直接编辑；驳回/要求补充时应要求说明。归档后真实刷新任务、档案版本和变更日志。长辈/家属健康档案页面通过 change-logs 接口展示可读历史，隐藏审核员 ID 和技术字段。测试并显示 409 并发冲突后的刷新动作。
```

### 阶段 25 前端专项

```text
实现护理端移动端“服务前健康摘要”只读页。入口来自当前已派任务，调用 GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary。布局优先显示风险、过敏、当前用药，再显示慢病与照护要点、审核通过病历摘要和近期服务。任务切换/返回列表时必须清空旧摘要，禁止前一位长辈资料残留。无资料展示真实空状态，不伪造内容；不显示内部联系方式、审核意见、存储路径或 ID。验证已派护理人员可看、其他护理人员和家属/长辈 403、阶段 24 归档后刷新可见新数据。
```

## 七、推荐实施顺序与联调门禁

1. 子阶段 19-A：先建立 Docker 化真实运行环境，固定部署、代理、上传、中文编码、健康检查和四端验收入口。
2. 子阶段 19-B：在 19-A 环境中建立 Redis 缓存失效与并发保护；MySQL 仍是事实来源。
3. 阶段 19：冻结健康档案字段、版本和权限，作为后续所有健康数据的唯一归档基础。
4. 阶段 20 与 22：可并行，但都要复用阶段 19 的长辈/家属权限和文件归属规则，并同步补充 19-B 的缓存失效事件。
5. 阶段 21：以阶段 20 的真实病历为输入，完成审核回显。
6. 阶段 23：以阶段 15 报告、阶段 19 档案为输入，建立“建议不直接归档”的边界。
7. 阶段 24：唯一正式归档入口，完成变更日志、并发保护和 Redis 失效。
8. 阶段 25：最后读取阶段 19、20/21、24 的已审核数据，验证护理端服务前摘要。

每完成一个阶段才能进入下一个依赖阶段；进入下一阶段前必须通过“真实写入 - 数据库检查 - 下一角色读取 - 越权 403 - 刷新一致性”五项门禁。
