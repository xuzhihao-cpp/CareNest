# 阶段18 MVP 全流程联调 前端验收

## 依据

- 参考整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 冲突时以正式开工文档为准：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 阶段18范围：从登录、绑定、档案、下单、派单、护理执行、报告、确认连续演示。
- 前置依赖：阶段1-17。

## 接口与 DTO

- `GET /api/v1/health`
- `GET /api/v1/admin/demo-data/status`
- 响应 DTO：`ready`、`accounts`、`scenarioCount`
- mock trace：
  - `mock-18-health`
  - `mock-18-admin-demo-data-status`
  - `mock-18-admin-demo-status-forbidden`
  - `mock-18-integration-status-empty`
  - `mock-18-integration-status-error`

说明：阶段1也使用 `GET /api/v1/health` 做首页健康检查，阶段18面板按 PDF 第40页的联调 DTO 单独封装，不改阶段1已有首页健康检查结构。

## 前端实现

- `frontend/src/api/stageEighteen.ts`
  - 封装阶段18 `GET /health` 联调状态。
  - 封装管理端 `GET /admin/demo-data/status`。
  - 非管理端访问管理端演示数据状态返回 `403`。
  - 提供阶段1-17主流程连续演示节点清单。
- `frontend/src/components/StageEighteenIntegrationPanel.vue`
  - 四端均可查看联调状态。
  - 管理端可看到 `admin/demo-data/status` 成功响应。
  - 非管理端显示管理端状态接口受保护。
  - 明确标记当前为 `contract mock` 或 `real api`。
- `frontend/src/types/stageEighteen.ts`
  - 补充阶段18响应 DTO、流程节点类型。
- `frontend/src/api/mockServerPaths.ts`
  - 登记 `GET /api/v1/admin/demo-data/status`。

## 验收结果

- 管理端阶段18面板显示 `ready=true`、`accounts=4`、`scenarioCount=17`。
- 管理端 `GET /api/v1/health` 返回 `mock-18-health`。
- 管理端 `GET /api/v1/admin/demo-data/status` 返回 `mock-18-admin-demo-data-status`。
- 家属端可查看 `GET /api/v1/health`，但管理端演示数据状态接口返回 `403`。
- 主流程节点显示 `13 / 13 READY`，覆盖登录、绑定、档案、下单、派单、护理执行、报告、确认和阶段17状态一致。
- 空数据 mock 展示 `404 数据不存在`，trace 为 `mock-18-integration-status-empty`。
- 错误 mock 展示 `500 服务异常`，trace 为 `mock-18-integration-status-error`。

## 截图

- `docs/stage-check/stage-18-admin-integration-status.png`
- `docs/stage-check/stage-18-family-admin-status-forbidden.png`
- `docs/stage-check/stage-18-integration-empty.png`
- `docs/stage-check/stage-18-integration-error.png`

## 验证命令

- `pnpm typecheck`
- `pnpm build:h5`
- Browser：`http://127.0.0.1:5173/#/pages/admin/index`、`http://127.0.0.1:5173/#/pages/family/index`
