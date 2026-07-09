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
