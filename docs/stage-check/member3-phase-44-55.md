# 成员3阶段44-55验收记录

## 交付范围

本次已完成阶段44-55的前端、后端、数据库和 Docker 整合：客服工单回访、评价投诉、护理申诉、护理评分、培训文章推荐、管理随访、基础/质量看板、演示数据重置和阶段55健康就绪字段。

阶段41-43的 AI 会话、风险审阅、协助单和客服工单作为阶段44回访的上游链路一并恢复并完成真实联调；阶段51家属端 `ACTIVE + HEALTH_VIEW` 随访读取也已接通。

## 契约与职责

- 基线：最新 `origin/main` 的接口、数据字典和成员1阶段32-55数据库结构。
- 服务端口：`${BACKEND_CARE_ADMIN_PORT:8082}`。
- 演示种子：`${DEMO_DATA_SEED_PATTERN:file:./db/seed/*.sql}`。
- 权限沿用冻结码，不新增评分专用权限；评分管理复用 `NURSE_APPEAL_REVIEW`。
- 所有关键写操作写入 `operation_log`。
- 演示重置只调用成员1维护的种子文件，不复制或改写种子内容。
- 运行页面不使用 mock，所有写入成功后均可在 MySQL 中核对。

## 阶段验收

| 阶段 | 成员3交付 | 结果 |
| --- | --- | --- |
| 44 | 既有客服工单回访及列表 | 已完成 |
| 45 | 家属评价投诉、管理端投诉查询与处理 | 已完成 |
| 46 | 护理申诉、申诉查询和管理审核 | 已完成 |
| 47-48 | 评分重算、评分查询、变更记录和护理端我的评分 | 已完成 |
| 49-50 | 完整文章读模型、发布/下线、风险推荐、必读门禁、阅读记录与 Redis 缓存 | 已完成 |
| 51 | 管理随访、事务内提醒创建、家属授权读取 | 已完成 |
| 52-53 | 真实基础/质量统计、30 秒 Redis 缓存与 MySQL 回源 | 已完成 |
| 54 | 演示数据重置与就绪状态 | 已完成 |
| 55 | 健康接口 `ready` 字段与演示就绪复核 | 已完成 |

## 关键规则

- 评价只允许订单家属或具有 `REPORT_VIEW` 的有效绑定家属提交，且同一评价人不得重复评价。
- 申诉审核不得替换原目标；审核通过后在同一事务触发评分重算。
- 评分根据当前指标、未驳回投诉和已通过申诉重新汇总，重复重算不产生无变化日志。
- 文章只有 `PUBLISHED` 才能推荐，并同时匹配订单服务和长辈风险标签。
- 必读文章必须具有可访问内容地址；护理端打开文章后才能确认阅读。
- 家属读取随访必须先验证 `FAMILY`、`ACTIVE` 绑定和 `HEALTH_VIEW`，缓存读取也不得早于权限校验。
- 护理端评分、推荐和阅读接口允许护理员访问；按冻结契约保留 ADMIN 管理场景，并继续校验管理权限。
- 看板只读取真实业务表，不使用常量或 mock 统计。
- 演示就绪要求五个固定演示账号均启用且角色正确、八类关键场景都有数据，并且没有未关闭的高危缺陷。

## 测试证据

- `Phase44To48SupportScoreServiceTest`：3 项规则测试。
- `Phase49To50TrainingServiceTest`：3 项文章与权限测试。
- `Phase51To55DeliveryServiceTest`：4 项随访、授权、统计和重置测试。
- `Phase44To55RepositoryIntegrationTest`：4 项 H2 MySQL 兼容模式真实 SQL 跨阶段测试。
- `CareAdminStatusServiceTest`：2 项阶段55健康与数据库就绪测试。
- `phase-49-55-full-stack-integration.md`：记录 Nginx 入口、五角色、MySQL、Redis 故障回源、Docker 与页面级真实验收。
- `phase-32-55-full-stack-integration.md`：记录阶段32-55最终总回归和阶段41-48重点验收。
- `docs/test/phase-41-55-full-stack-integration.mjs`：12组 Docker 真实接口、权限、MySQL、Redis 联调检查。

执行命令：

```powershell
mvn test
cd frontend
pnpm test:stage32-55
pnpm typecheck
pnpm build
cd ..
node docs/test/phase-41-55-full-stack-integration.mjs
```

测试资源仅为 Repository 验证建立最小 H2 表结构，不属于成员1数据库交付，不会进入运行时包。
