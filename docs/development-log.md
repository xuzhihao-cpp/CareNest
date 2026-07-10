# CareNest 开发日志

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
