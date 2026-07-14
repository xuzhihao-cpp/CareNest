# 阶段 23 验收记录

## 自动化

- `Phase23HealthSuggestionApiTest`: 真实 JDBC 事务、护理归属、来源真实性、结构化字段、409 幂等、角色加权限、管理分页、档案不变。
- 前端：`pnpm test:stage23`、`pnpm typecheck`、`pnpm build:h5`。

## Docker 真实链路

- [x] 护理账号从真实订单的服务记录和服务报告提交建议，状态为 `PENDING`。
- [x] 管理账号经统一入口读取待审核任务；最新任务返回 camelCase 当前档案值。
- [x] 重复请求返回 409；MySQL 联表确认建议、任务、操作日志各一条。
- [x] 家属提交返回 403，伪造服务记录来源返回 422；自动化覆盖非订单护理人员 403。
- [x] 提交前后健康档案 `archiveVersion=1`，完整 API 响应一致。
