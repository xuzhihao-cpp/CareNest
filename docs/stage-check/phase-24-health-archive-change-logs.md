# 阶段 24 健康档案变更记录验收

验收分支：`phase-21-24/health-review-integration`

## 修复内容

- 用户端变更记录改用 `/api/v1/elders/{elderId}/health-archive/change-logs`。
- 管理端审核工作台继续使用 `/api/v1/admin/elders/{elderId}/health-archive/change-logs`。
- 用户侧后端复用健康档案读取权限：长辈本人，或 `ACTIVE + HEALTH_VIEW` 家属。
- 查询兼容 MySQL 与 H2，JSON 业务字段在 Java 服务层解析。
- 未修改数据库结构，未增加 mock 或失败回退。

## 自动化验证

- `mvn -B -ntp -pl backend-user -Dtest=Phase19HealthArchiveApiTest test`：16 项通过。
- `pnpm test:stage24`：11 项通过。
- `pnpm typecheck`：通过。
- `pnpm build:h5`：通过。
- `git diff --check`：通过。

## 真实环境验证

- Docker `backend-user` 更新后健康检查为 `healthy`。
- 家属演示账号读取 `elder_001`：`code=0`，返回 8 条真实变更记录。
- 长辈演示账号读取本人 `elder_001`：`code=0`，返回 8 条真实变更记录。
- 管理员读取原管理侧接口：`code=0`，返回 8 条真实变更记录。
- 家属端实际页面不再显示“当前账号无权查看这位长辈的档案变更记录”，控制台无错误。
