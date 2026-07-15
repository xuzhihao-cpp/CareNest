# 阶段 26-31 优化版任务卡与前端 AI 提示词

> 依据：`互联网智慧护理平台完整设计文档.docx`、`互联网智慧护理平台正式开工文档第2版增强版.pdf`、`docs/team/phase-19-25-optimized-ai-task-cards.md`，以及阶段 01-25 的真实联调和缺陷复盘。
>
> 适用范围：阶段 26-31，包含护理注册与资质提交、护理资质审核、培训资格审核、护理推荐规则、护理推荐选择接入预约、服务前注意事项。
>
> 目标：以真实后端、真实 MySQL、Redis、MinIO 和四端页面完成护理准入、预约推荐到服务前强制风险确认的闭环。本文是阶段 26-31 的唯一执行指导；旧 PDF 中与已确认真实实现存在差异的内容，以本文“已确认修订”和冻结契约为准。
>
> “不得使用 mock”指任何用户可操作运行环境不得使用 mock、本地数组、静态 JSON、假成功或失败后回退数据。测试夹具可存在于自动化测试中，但不得成为页面数据源或验收证据。

## 一、PDF 前置固定约定（继续作为执行基线）

### 1. 本地开发环境

| 项目 | 固定约定 | 阶段 26-31 执行说明 |
| --- | --- | --- |
| 操作系统 | Windows 10/11 | 保持 |
| JDK | JDK 17 | 保持 |
| Maven | Maven 3.9.x | 保持 |
| Node.js | Node.js 20 LTS | 保持 |
| 前端包管理 | pnpm | 禁止混用 npm/yarn 生成新的锁文件 |
| 后端框架 | Spring Boot + MyBatis Plus | 保持仓库现有分层与统一异常处理 |
| 数据库 | MySQL 8.0 | 所有业务结果必须可从真实数据库验证 |
| 缓存 | Redis 7 | 推荐结果允许缓存，但 MySQL 始终是事实源 |
| 文件存储 | MinIO | 资质证明必须真实上传，禁止 base64 入库 |
| 容器环境 | Docker Desktop + Docker Compose | 必须能启动完整联调环境 |
| API 调试 | Apifox、Postman 或等效客户端 | 必须携带真实 Bearer Token |

| 服务 | PDF 端口 | 当前项目约定 |
| --- | --- | --- |
| 前端开发服务 | 3000 | 当前 Vite 使用 `5173`，以 `frontend/vite.config.ts` 为准 |
| 用户侧后端 `backend-user` | 8081 | 保持 |
| 护理/管理后端 `backend-care-admin` | 8082 | 保持 |
| MySQL | 3306 | 保持；Docker 映射端口以 `.env` 为准 |
| Redis | 6379 | 保持 |
| MinIO API | 9000 | 保持 |
| MinIO 控制台 | 9001 | 保持 |
| 本地演示 Nginx | 80 | 容器前端映射端口以 Compose 为准 |

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

推荐启动方式：

```powershell
docker compose --env-file docker/env/.env -f docker-compose.yml -f docker-compose.app.yml up -d
```

仅启动基础依赖：

```powershell
docker compose up -d mysql redis minio
```

开始业务联调前必须验证：MySQL 可查询、Redis `PING` 返回 `PONG`、MinIO 健康、两个后端健康检查通过、前端代理能同时访问 8081 与 8082。

### 2. 默认工作目录、目录职责、分支和提交

默认工作目录为仓库根目录：

```text
smart-nursing-platform/
```

对外分发本文时，所有路径均以仓库根目录为基准，不依赖个人电脑绝对路径。

```text
backend-user/          # 登录、长辈、家属、绑定、用户侧健康与文件能力
backend-care-admin/    # 护理、管理、订单、派单、准入、培训、推荐
frontend/              # 长辈、家属、护理、管理四端页面
db/schema/             # 完整建表 SQL
db/migration/          # 可重复执行或可检测的版本迁移
db/seed/               # 演示账号和阶段真实演示数据
docs/api/              # API 契约
docs/dictionary/       # 字段、枚举、权限码字典
docs/test/             # 自动化与人工测试说明
docs/stage-check/      # 每阶段真实验收证据
mock/                  # 只允许保留历史或测试夹具，不得接入运行页面
docker/                # 镜像、环境变量和部署说明
```

Git 规则：

- 禁止直接向 `main` 提交业务改动。
- 分支名遵循 README：`phase-26/nurse-qualification`、`phase-27/qualification-review`、`phase-28/training-review`、`phase-29/nurse-recommendation`、`phase-30/preferred-nurse`、`phase-31/attention-notices`；跨阶段整合可用 `phase-26-31/nurse-admission-recommendation`。
- 提交类型：`feat`、`fix`、`docs`、`db`、`test`、`chore`。
- 开始前执行 `git status`，不得覆盖或回滚其他成员未提交改动。
- 合并时先更新目标分支，再解决冲突；不得用 `git reset --hard` 或直接覆盖整个目录。

### 3. 全局 API 契约

| 规范项 | 固定内容 |
| --- | --- |
| API 前缀 | `/api/v1` |
| 统一返回 | `{ "code": 0, "message": "success", "data": {}, "traceId": "string" }` |
| 分页 | `{ "records": [], "total": 0, "page": 1, "size": 10 }` |
| 认证 | `Authorization: Bearer <token>` |
| 时间 | ISO 8601；前后端按 Asia/Shanghai 解释，无时区值不得被当作 UTC |
| 文件上传 | `POST /api/v1/files`，`multipart/form-data`，返回文件元数据 |
| 错误码 | `0` 成功；`400` 参数错误；`401` 未登录；`403` 无权限；`404` 不存在；`409` 状态冲突；`422` 业务规则不满足；`500` 服务异常 |

前端必须通过 `frontend/src/api` 的统一请求层访问接口。组件不得直接 `fetch` 业务 JSON；受保护文件预览可通过统一授权 Blob 适配器读取。同源受保护地址可附带 Token，跨域签名 URL 不得附带平台 Token。

接口归属：

- `backend-user:8081`：`/auth`、`/elder`、`/elders`、`/family` 用户资料与绑定等用户侧能力。
- `backend-care-admin:8082`：`/admin`、`/nurse`、`/orders`、`/service-items`，以及阶段 26-31 的准入、培训、推荐和服务前注意事项能力。
- 前端代理不得重写业务路径语义；Docker、开发模式和测试模式必须调用同一路径。

### 4. 角色、授权、状态与权限码

| 类别 | 固定值 |
| --- | --- |
| 角色 `roleCode` | `ELDER`、`FAMILY`、`NURSE`、`ADMIN`、`CUSTOMER_SERVICE` |
| 绑定 `bindingStatus` | `PENDING`、`ACTIVE`、`REJECTED`、`REVOKED` |
| 授权 `scopeCode` | `HEALTH_VIEW`、`HEALTH_EDIT`、`ORDER_CREATE`、`REPORT_VIEW`、`REPORT_CONFIRM`、`ARCHIVE_EDIT` |
| 通用审核 `auditStatus` | `PENDING`、`APPROVED`、`REJECTED`、`NEED_MORE` |
| 订单 `orderStatus` | `WAIT_DISPATCH`、`DISPATCHED`、`ACCEPTED`、`ON_THE_WAY`、`SERVING`、`WAIT_REPORT`、`WAIT_CONFIRM`、`COMPLETED`、`CANCELED` |
| 有效培训读取状态 | `PENDING`、`APPROVED`、`REJECTED`、`NEED_MORE`、`EXPIRED` |
| 注意事项等级 `noticeLevel` | `INFO`、`WARNING`、`CRITICAL` |
| 注意事项来源 `noticeSource` | `HEALTH_ARCHIVE`、`MEDICAL_FILE`、`SERVICE_ITEM`、`ORDER_CONTEXT` |

`EXPIRED` 是根据 `APPROVED + expiredAt <= 当前时间` 计算出的只读状态，不得作为新的数据库审核值写入。培训数据库仍只保存四种通用审核状态。

阶段 26-31 开工前必须在数据字典、权限种子和 `/auth/permissions` 中冻结以下权限码。若仓库已有等价权限码，统一复用一种，不得同时保留多套别名作为长期方案：

| 权限码 | 中文名称 | 默认角色 |
| --- | --- | --- |
| `NURSE_QUALIFICATION_SUBMIT` | 提交护理资质 | `NURSE` |
| `NURSE_QUALIFICATION_REVIEW` | 审核护理资质 | `ADMIN`、授权客服 |
| `NURSE_TRAINING_REVIEW` | 审核培训资格 | `ADMIN`、授权客服 |
| `NURSE_RECOMMEND_VIEW` | 查看护理推荐 | `FAMILY`、`ADMIN`、关联护理 |
| `NURSE_PREFERENCE_SELECT` | 选择偏好护理 | `FAMILY` |
| `NURSE_ATTENTION_ACK` | 确认服务前注意事项 | 被派 `NURSE` |
| `CARE_ATTENTION_REVIEW` | 审阅服务前注意事项 | `ADMIN`、授权客服 |

权限判断不能只看角色。管理/客服必须校验 `permissionCode`；家属必须校验订单归属、`ACTIVE` 绑定和 `ORDER_CREATE`；护理必须校验本人资源或关联订单。

### 5. 数据库、文件、缓存、演示账号和日志

阶段 26-31 的核心数据对象：

| 表/对象 | 用途 |
| --- | --- |
| `nurse_profile` | 护理身份、资质状态和接单资格摘要 |
| `nurse_certificate` | 资质申请、证书号、证明文件、技能、审核结果 |
| `nurse_training_record` | 培训批次、状态、通过/过期时间和审核说明 |
| `nurse_service_skill` | 护理服务技能与服务项目匹配 |
| `nurse_score` | 护理评分读取源 |
| `nurse_recommendation_log` | 每次推荐候选、得分、理由、可用性与选择追踪 |
| `nursing_order` | 订单与 `preferred_nurse_id` |
| `care_attention_notice` | 服务前风险、禁忌、重点观察项和确认记录 |
| `file_asset` | 资质证明文件元数据，不保存浏览器本地路径 |
| `operation_log` | 提交、审核、培训、偏好选择等关键操作日志 |

所有表包含项目统一审计字段。审核记录不可被后续提交覆盖；重新提交应创建新申请并保留历史。证件号码只保存业务冻结的脱敏值；完整身份证号若未来需要保存，必须另行安全评审、加密和权限隔离，本阶段不得擅自增加明文字段。

推荐缓存约定：

```text
键：recommend:nurses:{requestHash}
TTL：不超过 5 分钟
事实源：MySQL
失效条件：资质审核、培训审核、培训过期、技能变化、评分变化、排班变化、订单服务/时间/地址变化
```

Redis 不得替代 `nurse_recommendation_log`。缓存命中仍须在用户确认偏好时重新校验候选护理的实时资格、培训状态、可用性和订单状态。

固定演示账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 长辈 | `elder_demo` | `Demo@123456` |
| 家属 | `family_demo` | `Demo@123456` |
| 护理 | `nurse_demo` | `Demo@123456` |
| 管理员 | `admin_demo` | `Demo@123456` |
| 客服 | `cs_demo` | `Demo@123456` |

阶段种子至少准备：一个无申请护理、一个待审护理、一个需补充护理、一个资质通过但培训未通过护理、一个培训有效护理、一个培训过期护理、两个技能不同且评分不同的可推荐护理、一个没有候选人的服务场景。

### 6. mock 与真实运行规则

- 不新增任何运行时 mock 分支、mock 开关、测试按钮或“失败后展示演示数据”。
- 后端尚未完成时，前端必须显示真实不可用状态，不得伪造成功。
- 自动化测试可使用隔离 fixture，但需通过 API adapter 或纯规则函数测试，不得让 fixture 被生产 bundle 引用。
- 验收必须包含真实接口响应、真实数据库记录、Redis/MinIO 状态和真实角色页面截图。

## 附录 A：阶段 01-31 全量接口契约基线

### A.1 路由归属与调用规则

| 路由前缀 | 服务 | 调用规则 |
| --- | --- | --- |
| `/auth`、`/elder`、`/elders`、用户侧 `/family` | `backend-user` | 登录、绑定、档案和用户侧能力 |
| `/admin`、`/nurse`、`/orders`、`/service-items` | `backend-care-admin` | 订单、护理、审核、准入、培训、推荐 |
| `/files` | 后端统一文件入口 | 浏览器不得直接持有 MinIO 密钥 |

前端使用不带重复 `/api/v1` 的业务路径传给统一请求层。任何写操作成功后必须重新读取真实后端状态，而不是只修改前端本地对象。

### A.2 阶段 01-05：基础、认证、权限与请求层

| 阶段 | 方法与路径 | 核心响应/规则 |
| --- | --- | --- |
| 01 | `GET /api/v1/health`、`GET /api/v1/version` | 应用状态、名称、版本和服务器时间 |
| 02 | `POST /api/v1/auth/login` | `{username,password}` -> `{token,userId,displayName,roles,menus}` |
| 02 | `POST /api/v1/auth/logout`、`GET /api/v1/auth/me`、`GET /api/v1/auth/menus` | 登录态、当前用户和菜单 |
| 03 | `GET /api/v1/auth/permissions` | 返回真实 `permissions`；前端不得从 `/auth/me` 猜权限 |
| 03 | `POST /api/v1/admin/roles/{roleId}/permissions` | 管理员按权限更新角色权限并记录日志 |
| 04 | `GET /api/v1/elder/home-summary`、`GET /api/v1/family/home-summary` | 对应用户首页摘要 |
| 04 | `GET /api/v1/nurse/workbench-summary`、`GET /api/v1/admin/dashboard/overview` | 护理/管理工作台摘要 |
| 05 | 前端统一请求层 | `ApiResponse<T>`、`PageResult<T>`、Token、错误映射、无 mock 回退 |

### A.3 阶段 06-09：绑定、档案、服务项目和地址

| 阶段 | 方法与路径 | 核心请求/规则 |
| --- | --- | --- |
| 06 | `POST/GET /api/v1/family/bindings` | 家属发起和读取绑定；重复活动关系 `409` |
| 06 | `POST /api/v1/elder/bindings/{bindingId}/approve` | 长辈确认绑定或待确认范围 |
| 06 | `PUT /api/v1/family/bindings/{bindingId}/scopes` | ACTIVE 范围变更确认前不改变原授权 |
| 06 | `POST /api/v1/family/bindings/{bindingId}/revoke` | 撤销后不再出现在有效绑定列表 |
| 07 | `GET/PUT /api/v1/elders/{elderId}/profile` | 基础档案、联系人和并发版本 |
| 07 | `GET /api/v1/family/elders` | 家属可管理长辈列表 |
| 08 | `GET /api/v1/service-items`、`GET /api/v1/service-items/{serviceId}` | 已登录用户查看服务 |
| 08 | `POST/PUT/DELETE /api/v1/admin/service-items...` | 管理服务项目及引用保护 |
| 09 | `GET/POST /api/v1/elders/{elderId}/service-addresses` | 地址列表和新增 |
| 09 | `PUT/DELETE /api/v1/service-addresses/{addressId}` | 地址归属、默认地址和历史订单快照保护 |

### A.4 阶段 10-14：下单、派单、任务和服务记录

| 阶段 | 方法与路径 | 核心请求/规则 |
| --- | --- | --- |
| 10 | `POST /api/v1/family/orders` | `{elderId,serviceId,addressId,scheduledStart,preferredNurseId,remark}` |
| 10 | `GET /api/v1/family/orders`、`GET /api/v1/orders/{orderId}` | 家属订单与资源归属详情 |
| 11 | `GET /api/v1/admin/orders`、`GET /api/v1/admin/orders/{orderId}` | 管理分页、筛选和详情 |
| 12 | `POST /api/v1/admin/orders/{orderId}/dispatch` | 最终派单；偏好护理不等于派单 |
| 12 | `POST /api/v1/nurse/tasks/{taskId}/accept`、`POST /api/v1/nurse/tasks/{taskId}/status` | 护理任务按顺序流转 |
| 13 | `GET /api/v1/nurse/tasks`、`GET /api/v1/nurse/tasks/{taskId}` | 护理本人任务，状态与订单对齐 |
| 14 | `POST /api/v1/nurse/orders/{orderId}/service-records` | 真实服务记录，结束时间晚于开始时间 |
| 14 | `POST /api/v1/nurse/orders/{orderId}/vital-signs` | 生命体征记录 |
| 14 | `GET /api/v1/orders/{orderId}/service-records` | 按订单资源归属读取 |

### A.5 阶段 15-18：报告、确认、订单变更与集成

| 阶段 | 方法与路径 | 核心请求/规则 |
| --- | --- | --- |
| 15 | `POST /api/v1/orders/{orderId}/service-report/generate` | 必须已有服务记录 |
| 15 | `GET /api/v1/orders/{orderId}/service-report` | 按长辈、家属授权、护理关联或管理权限读取 |
| 16 | `GET /api/v1/elder/reports...`、`GET /api/v1/family/reports...` | 报告列表和待确认列表 |
| 16 | `POST /api/v1/elder/reports/{reportId}/ack` | 长辈确认，满意度 `0..100` |
| 16 | `POST /api/v1/family/reports/{reportId}/ack` | `ACTIVE + REPORT_CONFIRM` |
| 16 | `POST /api/v1/family/reports/{reportId}/archive-suggestions/decision` | 额外校验 `ARCHIVE_EDIT` |
| 17 | `POST /api/v1/family/orders/{orderId}/cancel`、`reschedule` | 家属取消/改期 |
| 17 | `POST /api/v1/admin/orders/{orderId}/cancel` | 管理取消并写状态日志 |
| 18 | `GET /api/v1/admin/demo-data/status` | 仅验收/管理使用，成品页面不显示系统检查入口 |

### A.6 阶段 19-25：健康档案、病历、反馈、审核和摘要

| 阶段 | 方法与路径 | 核心请求/规则 |
| --- | --- | --- |
| 19 | `GET/PUT /api/v1/elders/{elderId}/health-archive` | 家属编辑带版本，护理来源变更不能直接写档案 |
| 19 | `POST /api/v1/elders/{elderId}/medications` | 快速新增用药必须与 PUT 策略冻结一致 |
| 20 | `POST /api/v1/files` | 真实 multipart 上传 |
| 20 | `POST/GET /api/v1/elders/{elderId}/medical-files` | 病历登记和列表 |
| 21 | `GET /api/v1/admin/medical-files`、`GET /api/v1/admin/medical-files/{fileId}` | 管理病历列表和详情 |
| 21 | `POST /api/v1/admin/medical-files/{fileId}/review` | 审核结果和档案提取建议 |
| 22 | `POST /api/v1/elder/health-feedback` | 长辈本人结构化反馈 |
| 22 | `GET /api/v1/family/elders/{elderId}/health-feedback` | `ACTIVE + HEALTH_VIEW` |
| 23 | `POST /api/v1/orders/{orderId}/health-update-suggestions` | 护理基于真实服务记录/报告提交建议 |
| 23 | `GET /api/v1/admin/health-review-tasks` | 管理/客服按权限读取审核任务 |
| 24 | `GET /api/v1/admin/health-review-tasks/{taskId}` | 当前档案与来源建议对比 |
| 24 | `POST /api/v1/admin/health-review-tasks/{taskId}/archive` | 逐字段决策、事务、并发保护 |
| 24 | `GET /api/v1/elders/{elderId}/health-archive/change-logs` | 用户侧业务化变更历史 |
| 25 | `GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary` | 仅关联护理且开始服务前读取完整只读摘要 |

### A.7 阶段 26-31：护理准入、培训、推荐和注意事项（冻结接口）

| 阶段 | 方法与路径 | 请求/查询 | 成功 `data` | 权限与规则 |
| --- | --- | --- | --- | --- |
| 26 | `POST /api/v1/nurse/qualification-applications` | `{realName,idNoMasked,certificateNo,certificateFileIds,serviceSkillCodes}` | `{applicationId,auditStatus}` | `NURSE + NURSE_QUALIFICATION_SUBMIT`；PENDING/APPROVED 不得重复提交 |
| 26 | `GET /api/v1/nurse/qualification-applications/current` | 无 | 当前申请可展示读模型，见 A.8 | 护理本人；无申请返回 `404` |
| 26 | `GET /api/v1/admin/nurse-qualification-applications` | `auditStatus,page,size` | `{records,total,page,size}`，record 见 A.8 | `ADMIN/CUSTOMER_SERVICE + NURSE_QUALIFICATION_REVIEW` |
| 27 | `POST /api/v1/admin/nurse-qualification-applications/{applicationId}/review` | `{auditStatus,reviewComment}` | `{nurseId,qualificationStatus}` | 仅 PENDING；REJECTED/NEED_MORE 意见必填 |
| 28 | `GET /api/v1/nurse/training-status` | 无 | 当前培训可展示读模型，见 A.8 | 护理本人；管理不得借此冒充任意护理 |
| 28 | `POST /api/v1/admin/nurses/{nurseId}/training-review` | `{status,trainingBatch,expiredAt,remark}` | `{nurseId,trainingStatus,expiredAt}` | `ADMIN/CUSTOMER_SERVICE + NURSE_TRAINING_REVIEW`；资质先通过 |
| 29 | `POST /api/v1/orders/recommend-nurses` | `{elderId,serviceId,scheduledStart,addressId}` | `{nurses:[{nurseId,nurseName,score,matchedSkills,recommendReason,available}]}` | 家属需 `ACTIVE + ORDER_CREATE`；管理/关联护理按资源权限 |
| 29 | `GET /api/v1/orders/{orderId}/recommendations` | 无 | 同推荐列表 | 可访问订单的家属、管理、关联护理 |
| 30 | `PUT /api/v1/family/orders/{orderId}/preferred-nurse` | `{preferredNurseId}` | `{orderId,preferredNurseId,recommendReason}` | `FAMILY + ACTIVE + ORDER_CREATE`；仅 `WAIT_DISPATCH` |
| 30 | `GET /api/v1/family/orders/{orderId}/recommendation-view` | 无 | `{orderId,preferredNurseId,recommendReason}` | 同上；无偏好返回 `404` |
| 31 | `GET /api/v1/nurse/orders/{orderId}/attention-notices` | 无 | `{items:[{noticeId,level,content,source,requiredAck,acknowledged,acknowledgedAt}]}` | 被派护理本人；管理端按 `CARE_ATTENTION_REVIEW` 只读 |
| 31 | `POST /api/v1/nurse/orders/{orderId}/attention-notices/ack` | `{noticeIds}` | 同上，返回最新确认状态 | 被派护理本人 + `NURSE_ATTENTION_ACK`；必确认项未全确认不得开始服务 |

### A.8 阶段 26-28 可展示读模型修订

PDF 的最小响应只包含申请编号和状态，无法支撑“管理端查看申请、护理端回显驳回原因、管理端选择培训护理”的成品页面。阶段 26 开工时必须在 `docs/api`、DTO、测试和前端类型中同步冻结以下读模型；这属于现有 GET 接口的兼容扩展，不新增随意路径。

```json
{
  "applicationId": "qualification_xxx",
  "nurseId": "nurse_xxx",
  "nurseName": "张护士",
  "auditStatus": "PENDING",
  "realName": "张某",
  "idNoMasked": "**************1234",
  "certificateNoMasked": "CERT-****-8899",
  "certificateFiles": [
    {
      "fileId": "file_xxx",
      "originalName": "护理资格证.pdf",
      "mimeType": "application/pdf",
      "size": 102400,
      "previewable": true
    }
  ],
  "serviceSkillCodes": ["BASIC_CARE"],
  "reviewComment": null,
  "submittedAt": "2026-07-15T10:00:00+08:00",
  "reviewedAt": null
}
```

```json
{
  "nurseId": "nurse_xxx",
  "nurseName": "张护士",
  "qualificationStatus": "APPROVED",
  "trainingStatus": "APPROVED",
  "trainingBatch": "2026-07-A",
  "passedAt": "2026-07-15T10:00:00+08:00",
  "expiredAt": "2027-07-15T23:59:59+08:00",
  "remark": "年度培训通过"
}
```

前端不得显示 `applicationId`、`nurseId`、`fileId`。这些字段只用于请求关联和列表 key。管理端资质审核使用第一份读模型；管理端培训审核可从“资质已通过”的申请记录中选择护理，再读取/提交培训结果，不允许让审核员手填 `nurseId`。

### A.9 阶段 10、29、30 的统一预约策略

1. 家属选择长辈、服务、地址和时间后调用阶段 29 推荐接口。
2. 未创建订单时，家属可选择一个偏好护理，并在阶段 10 创建订单请求的 `preferredNurseId` 中提交；该护理必须来自当前有效推荐结果。
3. 已创建且仍为 `WAIT_DISPATCH` 的订单，使用阶段 30 PUT 修改偏好。
4. 服务、地址、时间任一变化后，旧推荐作废，必须重新推荐并重新选择。
5. 偏好护理不等于派单，不创建 `nurse_task`，不把订单改为 `DISPATCHED`。
6. 最终派单仍由阶段 12 管理端执行。管理端必须看到“家属偏好”及推荐理由，但可因排班等原因选择其他合格护理并填写派单备注。

## 二、阶段 26-31 总规则

### 1. 开始前必须完成的检查

1. 阅读 README、本文、数据字典、阶段 10/12/25/26-31 API 文档；阶段 19-25 文档只作为历史证据，不得覆盖本文契约。
2. 检查当前分支、`git status`、Controller、DTO、Repository 和现有测试，不覆盖其他成员改动。
3. 核对阶段 26-31 生产表是否真正存在。当前仓库接口骨架不代表生产数据库迁移已经完成。
4. 先冻结 A.8 可展示读模型、权限码、状态和 SQL，再并行开发；不得让每个成员各自猜字段。
5. 启动真实 Docker 环境，确认前端访问的是当前构建产物，避免“源码已改、容器仍运行旧 JAR/旧 dist”。

### 2. 当前仓库现实与必须处理的缺口

- `backend-care-admin` 已存在阶段 26-30 Controller、Service、DTO 和部分测试骨架，但不得据此直接判定阶段完成；阶段 31 仍须按本文接入现有任务状态机。
- 生产迁移需补齐 `nurse_certificate`、`nurse_training_record`、`nurse_service_skill`、`nurse_score`、`nurse_recommendation_log` 及 `nursing_order.preferred_nurse_id` 的真实结构和索引。
- 当前最小申请 DTO 不足以展示申请人、证书、技能和审核意见，必须按 A.8 同步扩展。
- 管理端不得要求输入申请编号、护理编号或文件编号。所有对象必须从真实列表中选择。
- 当前角色校验还需补齐 permissionCode、资源归属和 ACTIVE 绑定/scope 测试。
- 前端尚未完成阶段 26-31 成品页面时，不得用旧的测试面板或静态卡片假装接入。

### 3. 全阶段不可违反的实现规则

- 页面只显示中文业务名称，不显示 API、DTO、traceId、内部 ID、数据库字段名、枚举英文或 mock 文案。
- 写操作必须落库并记录日志；刷新、重新登录和另一角色页面都应读取到同一结果。
- 管理审核按钮不默认选择“通过”。每次审核必须由操作人明确选择决定。
- 列表、详情、提交都要防竞态：切换对象时旧请求不得覆盖新对象；提交 A 的错误不得显示到 B。
- 文件上传成功而业务登记失败时保留 `fileId` 供重试，防止重复上传和孤立文件；上传响应缺少 `fileId` 必须判为失败。
- 文件类型同时校验扩展名、MIME、大小和后端内容检测；预览只使用授权 Blob 或可信签名 URL。
- 身份证和证书信息按脱敏值展示；日志和错误消息不得包含完整敏感数据。
- 推荐必须可解释、可追溯、可复算。不能只返回一个护理编号，也不能让前端自行算推荐分。
- 资质或培训无效/过期护理不得进入推荐、不得被设为偏好、不得被最终派单。
- 所有日期使用结构化控件；培训通过的过期时间必须晚于当前时间。
- 三个移动端使用移动布局，管理端使用桌面工作台布局；按钮有稳定高度且文字垂直居中。

### 4. 推荐评分与解释规则

推荐至少考虑：资质有效、培训有效、服务技能匹配、护理评分、历史服务关系、预约时间可用性。资质和培训是硬过滤条件，不参与“低分仍推荐”。

建议首版可解释权重（如团队已有冻结权重，以已冻结配置为准）：

| 项目 | 分值 | 规则 |
| --- | --- | --- |
| 技能匹配 | 30 | 与服务项目要求技能的匹配程度 |
| 护理综合评分 | 25 | 来自 `nurse_score`，缺失时按明确默认策略处理 |
| 历史服务关系 | 20 | 曾服务当前长辈且无有效投诉 |
| 时间可用性 | 15 | 排班与现有任务不冲突 |
| 服务适配/距离等已具备信息 | 10 | 只使用系统已有可靠数据，不伪造定位 |

`recommendReason` 必须由后端根据命中因素生成中文原因，例如“培训资格有效，擅长基础护理，曾为该长辈服务，当前时段可预约”。前端只展示，不自行拼接虚假理由。

### 5. 每阶段统一交付物

- 数据库：`db/schema` 或 `db/migration`、索引、约束、可验证查询和演示 seed。
- 后端：DTO、Controller、Service、Repository、权限、事务、日志、缓存失效和自动化测试。
- 前端：真实 API adapter、类型、页面、结构化控件、加载/空/失败/无权限/冲突状态。
- 文档：数据字典、API 契约、阶段验收文档。
- 证据：真实 HTTP、数据库查询、Redis 验证、MinIO 文件、角色页面截图和 403/409/422 测试。

## 三、阶段详细任务卡

## 阶段 26：护理注册与资质提交

### 目标与边界

护理人员提交本人资质材料并查看当前审核状态；管理端能从真实列表看到待审核申请。阶段 26 不做审核决定，不授予接单资格。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 阶段组 | 护理准入、推荐与订单增强 |
| 模块 | 护理准入 |
| 前置依赖 | 阶段 2、真实文件上传 |
| 主要表 | `nurse_profile`、`nurse_certificate`、`file_asset` |
| 接口 | POST/GET 当前申请；管理端分页列表 |
| 验收 | 护理端能提交证书，管理端看到待审核申请 |

### 数据库任务

1. 创建或迁移资质申请与证书表，申请、文件、技能和审核信息可追溯。
2. 对同一护理的有效申请建立业务约束：`PENDING`/`APPROVED` 时禁止重复正式申请。
3. 保留历史申请；重新提交不覆盖旧审核记录。
4. `certificate_file_id` 必须引用真实 `file_asset` 且归属当前提交人。
5. 提供待审、需补充、已通过演示数据。

### 后端任务

1. 实现 A.7/A.8 DTO 和分页，校验所有文件存在、归属正确、类型和数量合法。
2. `serviceSkillCodes` 来自字典且去重；不得接收任意自由字符串。
3. `idNoMasked`、证书信息不得写入普通错误日志。
4. PENDING/APPROVED 重复提交返回 `409`；REJECTED/NEED_MORE 允许创建新申请。
5. 提交成功写 `operation_log`；GET current 只返回当前护理本人申请。
6. 若保留 ADMIN 管理场景访问 current，必须有明确护理资源上下文；禁止把管理员 userId 当 nurseId 入库。

### 前端任务

1. 护理端增加“准入资格”入口，显示当前资质和培训概览。
2. 证件姓名、脱敏证件号、证书号、技能、证明文件使用结构化表单；技能从真实字典选择。
3. 文件上传显示名称、类型、大小、进度和失败重试；成功后登记失败不得重复上传。
4. PENDING/APPROVED 时禁用重复提交；NEED_MORE/REJECTED 显示中文原因并允许重新整理材料。
5. 页面不显示申请编号、文件编号或英文状态。

### 必测场景与验收

- 护理首次提交成功，数据库、MinIO、operation_log 均有记录。
- 管理端刷新后出现同一真实申请，字段和文件可查看。
- 文件不属于本人、非法类型、缺少技能、重复提交分别失败。
- 网络中断或登记失败后可重试且不产生重复文件。
- 另一护理不能读取当前申请。

### AI 可直接执行的任务输入

```text
实现阶段26护理注册与资质提交。先阅读 docs/team/phase-26-31-optimized-ai-task-cards.md，严格使用 A.7 和 A.8 契约。只能使用真实后端、MySQL 和 MinIO，禁止运行时 mock、静态申请和假成功。护理端必须通过结构化表单选择技能并上传真实证明；提交、当前状态和管理端列表必须共享同一数据。补齐 401/403/409/422、文件归属、重复提交、失败重试和跨角色刷新测试。页面不得显示内部 ID、API/DTO 或英文状态。
```

## 阶段 27：护理资质审核

### 目标与边界

管理端或有权限客服对待审资质明确选择通过、驳回或需补充；护理端刷新后看到结果和业务化意见。阶段 27 不直接生成培训通过状态。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 前置依赖 | 阶段 26 |
| 主要表 | `nurse_profile`、`nurse_certificate`、`operation_log` |
| 接口 | `POST /admin/nurse-qualification-applications/{applicationId}/review` |
| 请求 | `{auditStatus,reviewComment}` |
| 响应 | `{nurseId,qualificationStatus}` |
| 验收 | 驳回/补充原因回显护理端；通过后可进入培训审核 |

### 后端任务

1. 只有 `PENDING` 可审核，重复或过期操作返回 `409`。
2. `auditStatus` 仅允许 `APPROVED/REJECTED/NEED_MORE`。
3. REJECTED/NEED_MORE 的 `reviewComment` 必填并限制长度；APPROVED 可填写说明。
4. 申请、护理资质摘要和 operation_log 在同一事务更新。
5. ADMIN/CUSTOMER_SERVICE 同时校验 `NURSE_QUALIFICATION_REVIEW`。
6. 审核响应和重新读取结果一致，不返回完整敏感证件信息。

### 前端任务

1. 管理端使用桌面三栏或列表+详情工作台：筛选列表、申请详情、审核决定。
2. 申请必须从列表选择，不允许输入 applicationId。
3. 默认审核决定为空；审核员逐项查看文件和技能后才能提交。
4. 切换申请时取消/失效旧详情请求，提交结果只作用于原申请。
5. 审核成功后重新读取该申请最新详情；筛选为待审导致记录移出时，显示明确完成状态后再选择下一条。
6. 护理端显示“待审核/已通过/未通过/需补充”和审核说明。

### 必测场景与验收

- 管理员和授权客服可审核；无权限客服、护理、家属返回 403。
- 未选择决定不能提交；需补充/驳回无意见不能提交。
- 两名审核员并发处理同一申请，只有一人成功，另一人 409 并刷新。
- 通过后资质状态有效但培训仍未通过，护理仍不能进入正式推荐。
- 护理端重新登录后仍能读取审核结果。

### AI 可直接执行的任务输入

```text
实现阶段27护理资质审核。管理端必须从真实分页列表选择申请，展示申请人、脱敏证件、证书、技能和证明文件，不能让用户填写内部编号。审核决定初始为空，只有 PENDING 可提交；驳回或需补充必须填写原因。校验 ADMIN/CUSTOMER_SERVICE 与 NURSE_QUALIFICATION_REVIEW 权限，处理并发 409、切换请求竞态和审核后真实刷新。护理端必须同步显示审核结果，但通过资质不等于培训通过或可接单。
```

## 阶段 28：培训资格审核

### 目标与边界

管理端维护已通过资质护理的培训批次、状态和过期时间；护理端查看本人培训资格和有效期。未通过或过期护理不能推荐、偏好或派单。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 前置依赖 | 阶段 27 |
| 主要表 | `nurse_training_record` |
| 接口 | `GET /nurse/training-status`；`POST /admin/nurses/{nurseId}/training-review` |
| 请求 | `{status,trainingBatch,expiredAt,remark}` |
| 响应 | `{nurseId,trainingStatus,expiredAt}` |
| 验收 | 护理端显示培训状态，推荐过滤无效护理 |

### 数据库与后端任务

1. 培训记录保留批次、状态、通过时间、过期时间、审核人和说明。
2. 仅资质 APPROVED 的护理可进入培训审核。
3. APPROVED 必须有未来 `expiredAt`；REJECTED/NEED_MORE 必须有 remark。
4. 当前有效状态按最新记录和过期时间计算，过期只读为 EXPIRED。
5. 审核成功使相关推荐缓存立即失效。
6. 管理端从资质已通过列表选择护理，不允许手填 nurseId。
7. 培训审核写 operation_log；无记录 GET 返回 404 或冻结的空业务状态，前后端统一。

### 前端任务

1. 护理端资格页同时显示资质状态、培训状态、批次和有效期，并明确“是否可接正式订单”。
2. 管理端从已通过资质护理列表选择对象，使用状态分段控件、批次输入和日期时间选择器。
3. APPROVED 时强制选择未来过期时间；改为其他状态时清理不适用字段。
4. EXPIRED 显示“培训已过期”，提供管理端重新审核入口，不伪装为普通驳回。
5. 页面不显示 nurseId 或英文状态。

### 必测场景与验收

- 资质未通过时培训审核返回 422。
- 通过但过期时间不在未来时前后端均拒绝。
- 培训有效护理可进入推荐；未通过、需补充、驳回、过期护理全部被过滤。
- 修改培训状态后推荐缓存失效，下一次请求立即反映新资格。
- 护理只能查看本人培训状态。

### AI 可直接执行的任务输入

```text
实现阶段28培训资格审核。管理端从资质已通过的真实护理列表选择对象，不允许填写 nurseId。培训状态复用 PENDING/APPROVED/REJECTED/NEED_MORE，EXPIRED 仅为按 expiredAt 计算的读取状态。APPROVED 必须设置未来过期时间；拒绝和需补充必须填写说明。审核后失效推荐缓存，护理端显示批次、有效期和是否可接单。用真实数据库验证无效或过期护理无法进入推荐。
```

## 阶段 29：护理推荐规则

### 目标与边界

根据资质、培训、技能、评分、历史关系和预约时间返回可解释的护理推荐列表。阶段 29 生成候选和日志，不直接派单。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 前置依赖 | 阶段 8、26、28，实际还依赖阶段 9/10 的长辈、地址和预约时间 |
| 主要表 | `nurse_recommendation_log`、`nurse_score`、`nurse_service_skill` |
| 接口 | `POST /orders/recommend-nurses`；`GET /orders/{orderId}/recommendations` |
| 响应 | `{nurses:[{nurseId,nurseName,score,matchedSkills,recommendReason,available}]}` |
| 验收 | 家属看到推荐列表和中文推荐原因 |

### 数据库与后端任务

1. 建立技能、评分和推荐日志结构，推荐日志保存请求条件、候选、分值、理由、可用性和操作人。
2. 校验长辈访问权、服务上架、地址归属、预约时间合法。
3. 先硬过滤资质/培训无效护理，再按冻结规则评分排序。
4. `available=false` 的候选可用于解释“暂不可约”，但不得被选择为偏好；正式推荐默认优先返回可用候选。
5. 相同条件可短时使用 Redis，但资格、培训、技能、评分、排班变化必须失效。
6. 推荐日志始终落库；不能只把结果放 Redis。
7. GET 订单推荐必须校验订单访问权，不得在没有日志时生成与订单条件不一致的候选。
8. 结果排序稳定：分数相同时使用明确次级排序，自动化测试可复现。

### 前端任务

1. 家属预约页在长辈、服务、地址、时间完整后出现“推荐护理”区域。
2. 推荐卡展示姓名、综合评分、匹配技能、推荐原因和可预约状态；不显示 nurseId。
3. 条件变化自动清空旧选择，并由用户触发或防抖后重新推荐，避免旧请求覆盖新条件。
4. 加载、无候选、无权限、服务异常分别显示中文业务状态，不回退静态护理名单。
5. 管理端订单详情可查看同一批推荐结果和理由；关联护理仅在业务需要时只读查看。

### 必测场景与验收

- 至少两个合格护理按冻结规则排序，理由与数据库因素一致。
- 培训过期、技能不匹配、时间冲突人员不会被误选。
- 地址不属于长辈、scope 缺失、订单不属于用户分别返回 403/422。
- 快速切换长辈或预约条件不会串页。
- Redis 命中与未命中结果一致；资格状态变化后旧缓存不再生效。
- 推荐日志可追溯到请求条件和候选理由。

### AI 可直接执行的任务输入

```text
实现阶段29护理推荐规则。推荐必须使用真实 MySQL 数据，资质 APPROVED 且培训有效是硬条件；再按技能、评分、历史服务和时间可用性排序。返回中文可解释 recommendReason 并写 nurse_recommendation_log。Redis 仅做最多5分钟缓存，所有资格/培训/技能/评分/排班变化必须失效。前端在预约条件完整后展示可读推荐卡，不显示 nurseId，不使用静态护理名单，处理条件切换竞态和无候选状态。
```

## 阶段 30：护理推荐选择接入预约

### 目标与边界

家属在预约流程中选择或修改偏好护理，管理端派单时查看偏好与理由。偏好不是承诺，也不改变订单状态或创建护理任务。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 前置依赖 | 阶段 10、29 |
| 主要表 | `nursing_order`、`nurse_recommendation_log` |
| 接口 | PUT 偏好护理；GET 偏好视图 |
| 请求 | `{preferredNurseId}` |
| 响应 | `{orderId,preferredNurseId,recommendReason}` |
| 验收 | 家属选择偏好后，管理端订单详情可见 |

### 后端任务

1. 仅订单所属家属且 `ACTIVE + ORDER_CREATE` 可操作。
2. 仅 `WAIT_DISPATCH` 可选择/修改；派单后返回 409。
3. 候选必须来自该订单当前有效推荐、available=true，并在提交瞬间重新校验资格和培训。
4. 更新 `preferred_nurse_id` 和 operation_log，不创建 `nurse_task`，不改变 `orderStatus`。
5. 服务、地址、时间变化时清除或重新验证旧偏好。
6. 管理派单接口仍执行最终护士选择和任务创建；不可把偏好自动当派单结果。

### 前端任务

1. 将推荐区嵌入阶段 10 预约页面，不新增割裂的测试页。
2. 推荐项使用单选卡或明确“设为偏好”控件，显示“偏好不代表最终派单”。
3. 新订单可在创建请求带 `preferredNurseId`；已创建 WAIT_DISPATCH 订单用阶段 30 PUT 修改。
4. 订单列表和详情显示偏好护理姓名及推荐原因，不显示 ID。
5. 管理端派单面板突出显示家属偏好，但管理员仍从所有有效护理中最终选择。
6. 提交后重新读取推荐视图和订单详情；不能只在本地改变选中样式。

### 必测场景与验收

- 家属选择偏好后订单仍为 WAIT_DISPATCH，数据库 `preferred_nurse_id` 与日志正确。
- 管理端刷新后看到同一偏好和理由。
- 管理员最终派其他合格护理时，派单正常且历史偏好仍可追溯。
- 无效、过期、不可用或不属于当前推荐的护理返回 422。
- 已派单订单修改偏好返回 409；前端刷新并关闭编辑入口。
- 地址/时间/服务变化后旧偏好不能静默保留。

### AI 可直接执行的任务输入

```text
实现阶段30护理推荐选择接入预约。把推荐与偏好直接整合进现有家属预约和订单详情，禁止另做内部ID测试表单。偏好护理必须来自当前订单有效推荐且实时可用；仅 WAIT_DISPATCH 可修改。写 preferred_nurse_id 和 operation_log，但不得创建 nurse_task 或改变订单状态。管理端派单页显示家属偏好和原因，最终派单仍由管理员执行。提交后重新读取真实订单与推荐视图，并覆盖 403/409/422、条件变化和跨端刷新测试。
```

## 阶段 31：服务前注意事项

### 目标与边界

根据阶段 25 已归档健康摘要、服务项目注意事项和当前订单生成风险、禁忌、重点观察项及本单必确认内容。被派护理必须确认全部 `requiredAck=true` 项后才能开始服务。本阶段不做医疗诊断，不自动修改健康档案。

### PDF 基线

| 字段 | 内容 |
| --- | --- |
| 阶段组 | 护理准入、推荐与订单增强 |
| 模块 | 护理执行 |
| 前置依赖 | 阶段 25；实际还依赖阶段 12/13 的派单任务状态 |
| 主要表 | `care_attention_notice` |
| 接口 | GET 注意事项；POST 批量确认 |
| 请求 | `{noticeIds}` |
| 响应 | `{items:[{noticeId,level,content,source,requiredAck}]}`，按 A.7 扩展确认状态 |
| 验收 | 护理端能看到并确认注意事项；必确认项未确认不得开始服务 |

### 数据库任务

1. 建立 `care_attention_notice`，至少保存订单、护理任务、等级、内容、来源类型/来源标识、是否必确认、确认人和确认时间。
2. 对同一订单、同一来源和同一规范内容建立幂等键，重复读取不得无限生成重复提醒。
3. 注意事项是本单服务前快照；来源档案后续变化不能静默改写已确认记录，应按版本/重新生成规则留痕。
4. 保存内容时不得复制完整敏感病历，只保存完成护理所需的最小风险说明和来源类型。

### 后端任务

1. 仅从已归档健康数据、审核通过病历摘要、服务项目注意事项和订单上下文生成；禁止使用未审核病历或前端自定义内容。
2. `level` 冻结为 `INFO/WARNING/CRITICAL`，前端映射为“提示/重点/高风险”；不得使用自由字符串。
3. GET 只允许被派护理本人读取；管理端按权限只读审阅。非关联护理返回 403。
4. POST 确认校验 noticeId 均属于当前订单、当前护理可见且尚未确认；重复确认应幂等返回最新状态。
5. 阶段 12/13 的“开始服务”状态流转必须在后端检查所有 `requiredAck=true` 项。缺失确认返回 409/422，不能只靠按钮禁用。
6. 订单取消后不可确认；任务重新派给另一护理时，根据业务冻结规则清理旧护理确认或要求新护理重新确认。
7. 确认写操作日志；响应不返回内部档案版本、病历 ID 或对象存储路径。

### 前端任务

1. 在护理任务“查看健康摘要”之后加入“服务前注意事项”，与现有护理任务页整合，不创建独立测试页面。
2. 按等级分组显示业务化内容、来源中文名称和“必须确认”标识，不显示 noticeId/source 枚举。
3. 必确认项使用明确勾选控件；普通提示只读。未完成全部必确认项时，“开始服务”按钮禁用并显示还差几项。
4. 批量确认成功后重新读取 GET；刷新或重新登录仍显示已确认状态。
5. 任务切换时立即清空上一个订单注意事项并取消旧请求，避免长辈风险串页。
6. 后端 409 表示状态已变化时刷新任务和注意事项；不得在前端强行进入服务中。
7. 高风险视觉突出但不使用恐吓性文案，不提供自动诊断或改药操作。

### 必测场景与验收

- 被派护理能看到与真实健康档案/服务项目一致的注意事项。
- 未确认必确认项时，直接调用开始服务接口也被后端拒绝。
- 全部确认后可正常开始服务，确认人和时间落库。
- 重复 GET 不产生重复行，重复 ACK 幂等。
- 非关联护理、家属、长辈访问返回 403；管理端只读权限正确。
- 重新派单后新护理必须按冻结规则重新确认。
- 快速切换两条任务不出现跨长辈注意事项。

### AI 可直接执行的任务输入

```text
实现阶段31服务前注意事项。基于阶段25已归档健康摘要、服务项目和订单生成真实注意事项，写入 care_attention_notice，禁止 mock 和前端自造风险。GET 仅关联护理可读，ACK 只确认当前订单 noticeIds。requiredAck=true 未全部确认时，后端状态机必须阻止开始服务；不能只禁用前端按钮。护理端按提示/重点/高风险显示中文内容和确认状态，不显示内部ID/枚举，处理任务切换竞态、重复读取幂等、重复确认、取消和重新派单场景。
```

## 四、前端实现 AI 主提示词

```text
你是 CareNest 智慧护理平台阶段26-31的前端负责人，主要在 frontend/ 内工作。开始前必须阅读 README.md、docs/team/phase-26-31-optimized-ai-task-cards.md、现有阶段10/12/25页面、统一请求层、角色应用入口和对应 API 文档。本文是本阶段唯一任务指导；旧阶段任务卡只用于了解历史，不得覆盖本文冻结契约。

硬性要求：
1. 只能通过真实后端接口获取和提交数据，禁止运行时 mock、静态护理名单、假成功、失败回退和测试按钮。
2. API adapter、TypeScript 类型、响应结构校验、业务规则函数、页面组件和自动化测试分层实现。
3. 权限读取使用 GET /auth/permissions；不能从 /auth/me 猜 permissionCodes。角色、资源归属、ACTIVE 绑定和 scope 均要正确处理。
4. 不让用户输入 applicationId、nurseId、fileId、orderId；必须从真实列表或业务上下文选择。
5. 页面不显示 API、DTO、traceId、内部ID、数据库字段名和英文枚举。所有状态、技能、错误和说明使用中文业务文案。
6. 护理端/家属端为移动端，管理端为桌面网页。按钮最小触控高度稳定，文字垂直居中；文本不得溢出或把中文挤成逐字换行。
7. 上传文件校验扩展名、MIME、大小和响应 fileId；上传成功但登记失败时保留文件供重试。受保护预览使用统一授权 Blob；跨域签名 URL 不附带 Token。
8. 所有列表、详情、提交实现请求序号或 AbortController，防止切换对象后旧响应覆盖新页面。
9. 审核决定初始为空；驳回/需补充意见必填。日期时间使用结构化选择器，培训通过的有效期必须晚于当前时间。
10. 推荐条件变化必须清空旧选择；推荐卡展示姓名、评分、技能、中文原因和可用性，不显示 nurseId。偏好护理明确提示“不代表最终派单”。
11. 服务前注意事项按等级和是否必确认展示；未确认全部 requiredAck 项时关闭开始服务入口，同时正确处理后端状态门禁错误。
12. 所有写操作成功后重新读取真实接口；失败时保留用户可恢复输入，并按 400/401/403/404/409/422/500 显示业务化提示。
13. 每阶段增加纯规则测试和组件/接口测试，运行 pnpm typecheck、对应阶段测试、build:h5 和 git diff --check。真实联调前不得宣称完全完成。

实现顺序：先读现有代码和契约 -> 冻结类型与状态中文映射 -> API adapter 与响应校验 -> 规则测试 -> 页面 -> 角色入口 -> 竞态与错误处理 -> 响应式视觉检查 -> 真实接口联调 -> 验收文档。

不得修改后端或数据库来掩盖前端问题。若真实接口与本文冻结契约不一致，记录准确差异和请求/响应证据，停止伪造数据并交由全栈负责人统一修订。
```

## 五、前端阶段专项提示词

### 阶段 26 前端专项

```text
只实现阶段26前端。护理端新增准入资格页面，真实上传证书并提交申请；显示当前审核状态和历史审核说明。技能使用字典选择，证件使用脱敏格式。PENDING/APPROVED 禁止重复提交，NEED_MORE/REJECTED 可重新整理后提交。管理端列表必须读取真实分页数据。不得显示任何内部编号。
```

### 阶段 27 前端专项

```text
只实现阶段27前端。管理端做桌面资质审核工作台，申请从真实列表选择，详情展示脱敏资料、技能和授权文件预览。审核决定默认未选择，驳回或需补充必须填写原因。实现权限接口、分页筛选、切换竞态、并发409和审核后详情刷新。护理端同步显示结果。
```

### 阶段 28 前端专项

```text
只实现阶段28前端。管理端从资质已通过列表选择护理，维护培训状态、批次和未来有效期；不允许输入 nurseId。护理端显示培训状态、批次、到期时间和是否具备接单资格。EXPIRED 显示中文过期状态，不作为写入枚举。覆盖日期边界和权限失败。
```

### 阶段 29 前端专项

```text
只实现阶段29前端。将真实护理推荐接入现有预约条件，展示姓名、评分、匹配技能、推荐原因和可用状态。服务、地址、长辈或时间变化时清空旧结果和偏好，并防止旧请求串页。没有候选时显示真实空状态，不使用静态护理数据。
```

### 阶段 30 前端专项

```text
只实现阶段30前端。把偏好护理选择整合进现有预约和订单详情。新订单通过阶段10请求提交偏好，已有 WAIT_DISPATCH 订单用阶段30 PUT 修改；派单后关闭编辑并处理409。家属端和管理端显示护理姓名与原因，管理端最终派单仍独立执行。提交后必须重新读取真实后端。
```

### 阶段 31 前端专项

```text
只实现阶段31前端。把服务前注意事项整合到护理任务和阶段25健康摘要流程，按等级显示中文风险、禁忌和重点观察项。requiredAck 项使用勾选确认，未全确认时不能开始服务；确认成功后重新读取真实状态。任务切换必须清空旧数据并取消旧请求。禁止显示 noticeId/source 英文值，禁止前端自造注意事项或绕过后端状态门禁。
```

## 六、全角色联调与验收矩阵

| 场景 | 护理端 | 家属端 | 管理/客服端 | 数据/状态 |
| --- | --- | --- | --- | --- |
| 提交资质 | 提交真实文件和申请 | 无入口 | 待审列表出现 | 文件、申请、日志一致 |
| 需补充 | 显示原因并可重提 | 无入口 | 明确选择并填写意见 | 原申请保留，新申请另建 |
| 资质通过 | 显示已通过但培训未完成 | 无入口 | 可进入培训审核 | 尚不可推荐/派单 |
| 培训通过 | 显示批次和有效期 | 可在推荐中看到 | 审核记录可追溯 | 有效期未来 |
| 培训过期 | 显示已过期 | 推荐中不可选 | 可重新审核 | Redis 推荐缓存失效 |
| 推荐护理 | 关联订单可按权限只读 | 看姓名、技能、评分、理由 | 订单详情可看 | 日志记录全部候选 |
| 选择偏好 | 不自动收到任务 | 可选/改偏好 | 派单页看到偏好 | 订单仍 WAIT_DISPATCH |
| 最终派单 | 被派后收到任务 | 看到最终安排 | 管理执行派单 | 创建任务并进入 DISPATCHED |
| 服务前注意事项 | 阅读并确认必确认项 | 无操作入口 | 按权限只读审阅 | 确认记录与任务归属一致 |
| 开始服务门禁 | 全部确认后才能开始 | 看到正常服务状态 | 可审计被拒绝原因 | 未确认时订单/任务状态不变 |

必测权限：

1. NURSE 只能提交和查看本人资质、培训。
2. ADMIN 和 CUSTOMER_SERVICE 必须同时具备对应审核 permissionCode。
3. FAMILY 推荐和偏好必须匹配订单长辈的 ACTIVE 绑定与 ORDER_CREATE。
4. 非订单关联护理不可读取订单推荐。
5. ELDER 不开放阶段 26-31 管理操作。

必测错误：401、403、404、409、422、500；文件服务不可用；Redis 不可用时降级到 MySQL；数据库事务失败；重复提交；并发审核；推荐空列表；偏好候选刚好过期；必确认项缺失；注意事项重复确认；任务重新派单。

## 七、推荐实施顺序与完成门禁

1. **契约冻结**：A.8 读模型、权限码、状态、技能字典和评分规则完成评审。
2. **数据库先行**：阶段 26-31 migration、索引、外键/业务约束和 seed 可在空库执行。
3. **阶段 26**：真实文件、提交、当前状态、管理列表端到端通过。
4. **阶段 27**：审核事务、权限、并发和护理端回显通过。
5. **阶段 28**：培训有效期、过期计算和推荐资格门禁通过。
6. **阶段 29**：推荐过滤、排序、解释、日志、Redis 一致性通过。
7. **阶段 30**：偏好与阶段 10/12 串联，订单状态和管理派单保持正确。
8. **阶段 31**：真实注意事项生成、确认幂等和开始服务后端门禁通过。
9. **自动化**：两个后端全部测试、前端阶段测试、typecheck、build、契约检查全部退出码 0。
10. **真实角色验收**：五个演示账号走完允许与拒绝场景，刷新和重新登录后状态一致。
11. **Docker 验收**：重新构建镜像并强制重建容器；验证运行容器确实使用当前 JAR 和 dist，而不是旧产物。
12. **证据归档**：`docs/stage-check/phase-26-*` 至 `phase-31-*` 包含接口、数据库、Redis/MinIO、页面和权限证据。

只有同时满足以下条件才能标记阶段 26-31 完成：

- 无运行时 mock、无内部 ID 输入、无技术字段展示。
- 数据库迁移和演示 seed 完整，真实数据可重复验证。
- 资质、培训、推荐、偏好、最终派单状态串联正确。
- 无效/过期护理在推荐、偏好和派单三层都被阻止。
- 必确认注意事项未完成时，前后端都不能把任务推进到服务中。
- 全角色权限与资源归属测试通过。
- 所有自动化命令通过且 Docker 页面完成视觉验证。
