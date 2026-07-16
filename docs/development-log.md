# CareNest 开发日志

## 2026-07-16

### 成员4：阶段 34-40 前端开发

- 阅读成员4阶段 34-40 文档、`docs/api/phase-34-40-care-metric-api.md` 和前端视觉规范，确认本轮成员4只交付前端页面、API 封装、规则校验和验收记录。
- 新增阶段34-40前端类型、真实接口封装和业务规则校验，覆盖护理指标配置、订单留档清单、护理留档、留档审核、指标完成校验、未完成原因证明和豁免审核。
- 管理端新增 `护理质控` 入口，按 `CARE_METRIC_CONFIG_MANAGE` 和 `CARE_EVIDENCE_REVIEW` 权限开放配置、清单生成、留档审核和豁免审核。
- 护理端新增 `质量留证` 入口，从真实护理任务进入订单质控流程，文件先上传至既有 `POST /api/v1/files` 获取 `fileId`，再提交留档或原因证明。
- 新增阶段34-40前端自动化检查脚本和阶段验收记录，明确不新增运行时 mock、不伪造后端未返回的业务详情、不声明后端或数据库额外交付。

## 2026-07-15

### 成员1：阶段 26-31 数据库与数据规范

- 阅读 `need/phase-26-31-optimized-ai-task-cards.md`，按成员1职责限定为数据库结构、迁移、权限种子、演示数据、数据字典、Redis 数据规范和验收说明。
- 新增阶段 26-27 护理准入表：`nurse_profile`、`nurse_certificate`，保留资质申请历史，证件号仅保存脱敏值，并用组合外键约束资质文件归属。
- 新增阶段 28 培训记录表：`nurse_training_record`，保留培训批次、状态、通过时间、过期时间、审核人和说明；`EXPIRED` 仅作为读取计算状态，不入库。
- 新增阶段 29-30 推荐与偏好结构：`nurse_service_skill`、`nurse_score`、`nurse_recommendation_log`，并为 `nursing_order` 补充 `preferred_nurse_id`、推荐原因快照和推荐日志关联字段。
- 新增阶段 31 服务前注意事项表：`care_attention_notice`、`care_attention_ack`，用 `notice_hash` 防止重复生成同一订单同一来源同一内容的提醒。
- 新增 `db/seed/phase-26-31-demo-data.sql`，冻结阶段 26-31 权限码，并提供无申请、待审、需补充、资质通过但培训未过、培训有效、培训过期、两个可推荐护理、无候选服务、偏好护理和注意事项演示数据。
- 新增 `docs/dictionary/phase-26-31-nurse-admission-data-dictionary.md`，记录字段、状态、权限码和推荐 Redis 缓存规则：`recommend:nurses:{requestHash}` 最多 5 分钟，MySQL 推荐日志始终为事实源。
- 新增 `docs/stage-check/member1-phase-26-31.md`，记录成员1交付边界与数据库验证命令；不声明后端/前端/Redis 客户端/MinIO 对象创建已经完成。

## 2026-07-11

### 成员1：阶段 19-25 数据库与数据规范

- 阅读最新 `need/phase-19-25-optimized-ai-task-cards (2).md`，按当前要求限定为成员1职责：数据库结构、迁移、演示数据、数据字典、Redis 数据规范和验收说明。
- 新增阶段 19 健康档案表：`health_archive`、`chronic_disease`、`medication_plan`、`allergy_record`、`risk_tag`、`care_plan`。
- 新增阶段 20 病历资料表：`file_asset`、`medical_file`，只保存对象存储元数据，不保存 MinIO 密钥或文件正文。
- 新增阶段 22 长辈健康反馈表：`elder_health_feedback`、`voice_command_log`。
- 新增阶段 23 健康档案变更建议表：`health_update_suggestion`，明确建议不直接覆盖正式健康档案。
- 新增阶段 24 审核任务扩展脚本：为 `health_info_review_task` 补充 `suggestion_id`、`task_type`，并兼容 `NEED_MORE`、`MEDICAL_FILE`、`SUGGESTION`。
- 新增 `db/seed/phase-19-25-demo-data.sql`，提供健康档案、病历资料、反馈、建议和待审核任务演示数据。
- 新增 `docs/dictionary/phase-19-25-health-data-dictionary.md`，冻结字段、状态和 Redis key/TTL/敏感性规则。
- 新增 `docs/stage-check/member1-phase-19-25.md`，记录成员1交付边界和数据库验证命令。
- 已撤回本轮中误加的后端、前端、Redis 客户端实现改动；当前不声明成员2/3/4功能完成。

## 2026-07-08

### 成员1：数据库与数据规范负责人

- 阅读 `need/` 中的总开工文档和成员1专属开工文档。
- 确认成员1职责：数据库、数据字典、状态字典、初始化 SQL、演示数据、统计 SQL、字段变更审核。
- 确认固定技术与环境：MySQL 8.0、Redis 7、MinIO、Docker Compose、数据库名 `smart_nursing`。
- 确认成员1阶段范围：阶段 2、3 可并入阶段 5 前主线；阶段 6、7、8、9 依赖阶段 5 或更后置阶段，暂缓合并。
- 创建成员1分阶段路线图：`docs/team/member1-database-roadmap.md`。
- 新增阶段 2-3 数据库建表 SQL 和演示数据 SQL。
- 同步更新 `docs/dictionary/data-dictionary.md`，补充阶段 2-3 数据库字段，并按总文档固定枚举修正部分状态值。
- 已执行阶段 2-3 SQL 到本地 MySQL 容器 `carenest-mysql`，验证建表和演示数据插入成功。
- 修正 Windows PowerShell 管道执行 SQL 造成中文演示数据变成 `?` 的问题；改用 `docker cp` + 容器内 `mysql --default-character-set=utf8mb4` 执行中文 seed。
- 提交前复核发现旧的 noop 演示密码写法与“密码不得明文保存”冲突，已改为 bcrypt 哈希并重新执行 seed 验证。
- 按阶段 5 合并测试要求，删除阶段 6-9 的建表 SQL、演示数据和验收完成声明，避免提前合并后置阶段内容。
- 创建验收记录：`docs/stage-check/member1-phase-02-03.md`。
- 下一步：等待阶段 5 合并后，继续推进阶段 6-9 数据库交付。

## 2026-07-09

### 成员1：阶段 6-10 数据库继续开发

- 从远端 `main` 拉取已合并的阶段 1-5、成员1阶段 2-3、前后端适配内容。
- 基于最新 `main` 创建分支 `phase-06-10/member1-db-baseline`。
- 新增阶段 6-10 建表 SQL：绑定授权、长辈基础档案、服务项目、服务地址、预约订单和订单状态日志。
- 新增阶段 6-10 演示数据 SQL：授权范围、长辈档案、绑定关系、服务项目、服务地址和 `WAIT_DISPATCH` 演示订单。
- 同步更新 `docs/dictionary/data-dictionary.md`，补充阶段 6-10 数据库字段和状态枚举。
- 创建验收记录：`docs/stage-check/member1-phase-06-10.md`。
- 已执行阶段 6-10 SQL 到本地 MySQL 容器 `carenest-mysql`，验证表结构、演示数据和中文字符正常。
- 根据第一次合并后的组长提示，补齐 `backend-user` 认证落库实现：`DemoAuthRepository` 改为 MyBatis-Plus Mapper 访问 MySQL，登录密码改为 BCrypt 校验，访问令牌改为 JWT，并继续沿用现有 Controller/Service 对外接口。
- 修正阶段 2-3 seed 与接口演示账号约定：统一 `elder_demo/family_demo/nurse_demo/admin_demo/cs_demo`、`elder-001/family-001/nurse-001/admin-001/cs-001` 和 `Demo@123456` BCrypt 哈希。
- 已执行阶段 2-3、6-10 SQL 到本地 MySQL 容器 `carenest-mysql`，验证新旧演示账号清理、权限顺序、阶段 10 订单引用均正常。
- 已执行 `mvn -pl backend-user -DskipTests compile` 和 `mvn -pl backend-user test`，后端认证与权限接口测试 13 项通过。
- 下一步：提交阶段 6-10 数据库基线 PR，等待成员2、成员3、成员4围绕接口与页面联调。

### 成员1：阶段 11-18 数据库继续开发

- 基于已推送的 `phase-06-10/member1-db-baseline` 创建分支 `phase-11-18/member1-db-baseline`。
- 阅读总开工文档中阶段 11-18 的接口和数据库表要求，确认成员1只交付数据库、状态字典、seed 和验收证据。
- 新增阶段 11-18 建表 SQL：订单查询索引、护理任务、服务记录、生命体征、服务报告、报告确认、健康信息审核任务和订单状态日志索引。
- 新增阶段 11-18 演示数据 SQL：将 `order_001` 推进为派单、接单、服务、报告、家属确认和完成的闭环演示链路。
- 同步更新 `docs/dictionary/data-dictionary.md`，补充任务状态、报告状态、报告明细类型、确认结果和健康信息审核状态。
- 创建验收记录：`docs/stage-check/member1-phase-11-18.md`。
- 已执行阶段 11-18 SQL 到本地 MySQL 容器 `carenest-mysql`，验证护理任务、服务记录、生命体征、服务报告、报告确认、健康信息审核任务和订单状态链路正常。

## 2026-07-10

### 成员1：演示数据一致性修复

- 排查长辈档案关联数据时发现 `elder_family_binding`、`service_address` 中历史演示数据仍保留 `family_001`，与当前演示账号和订单使用的 `family-001` 不一致。
- 修正 `db/seed/phase-06-09-demo-data.sql`：在绑定关系和服务地址的 `ON DUPLICATE KEY UPDATE` 中补充 `family_id = VALUES(family_id)`，避免旧库重复执行 seed 后继续保留错误 ID。
- 新增 `db/migration/fix-family-id-demo-data.sql`，用于将已存在数据中的 `family_001` 迁移为 `family-001`。
- 已更新本地 MySQL 容器 `carenest-mysql`，验证 `elder_family_binding`、`service_address`、`nursing_order` 的 `family_id` 均统一为 `family-001`。

## 2026-07-15 Member 1 Phase 32-55 Complete Database Delivery

- Scope stayed within member-1 database and data standards: MySQL schema, migration entry, demo seed data, dashboard SQL, dictionaries, Redis data policy, verification SQL, and stage-check record.
- Added phase 32-55 database objects for reminders, care metrics, order metric checklists, evidence review, metric exception proof, AI audit logs, assistance/customer-service tickets, reviews, complaints, nurse appeals, score change logs, training articles, reading records, follow-ups, and final bug tracking.
- Added `db/statistics/phase-52-53-dashboard-statistics.sql` so dashboard phases aggregate from real business tables instead of fake summary tables.
- Added `docs/dictionary/phase-32-55-complete-data-dictionary.md`, `docs/test/member1-phase-32-55-db-check.sql`, and `docs/stage-check/member1-phase-32-55.md` to freeze statuses, permissions, Redis key policy, sensitive-data boundaries, and acceptance checks.
- This entry does not claim backend endpoints, frontend pages, Redis client code, or MinIO object creation are complete.
