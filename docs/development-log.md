# CareNest 开发日志

## 2026-07-08

### 成员1：数据库与数据规范负责人

- 阅读 `need/` 中的总开工文档和成员1专属开工文档。
- 确认成员1职责：数据库、数据字典、状态字典、初始化 SQL、演示数据、统计 SQL、字段变更审核。
- 确认固定技术与环境：MySQL 8.0、Redis 7、MinIO、Docker Compose、数据库名 `smart_nursing`。
- 确认成员1阶段范围：阶段 2、3、6、7、8、9 作为首批数据库基础；后续继续推进阶段 18、19、20、24、26、29、31、32、33、34、42、47、51、52、53、54、55。
- 创建成员1分阶段路线图：`docs/team/member1-database-roadmap.md`。
- 新增阶段 2-9 数据库建表 SQL 和演示数据 SQL。
- 同步更新 `docs/dictionary/data-dictionary.md`，补充阶段 2-9 数据库字段，并按总文档固定枚举修正部分状态值。
- 已执行阶段 2-9 SQL 到本地 MySQL 容器 `carenest-mysql`，验证建表和演示数据插入成功。
- 修正 Windows PowerShell 管道执行 SQL 造成中文演示数据变成 `?` 的问题；改用 `docker cp` + 容器内 `mysql --default-character-set=utf8mb4` 执行中文 seed。
- 提交前复核发现旧的 noop 演示密码写法与“密码不得明文保存”冲突，已改为 bcrypt 哈希并重新执行 seed 验证。
- 创建验收记录：`docs/stage-check/member1-phase-02-09.md`。
- 下一步：继续推进阶段 10-18 订单主链数据库表与订单状态日志。
