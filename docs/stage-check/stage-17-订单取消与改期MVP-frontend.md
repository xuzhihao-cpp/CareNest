# 阶段17 订单取消与改期 MVP 前端验收

## 依据

- 参考整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 冲突时以正式开工文档为准：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 阶段17范围：家属或管理端对未服务订单取消、改期并保留状态日志。
- 前置依赖：阶段10预约下单。

## 接口与 DTO

- `POST /api/v1/family/orders/{orderId}/cancel`
- `POST /api/v1/family/orders/{orderId}/reschedule`
- `POST /api/v1/admin/orders/{orderId}/cancel`
- 请求 DTO：`reason`、`newScheduledStart`
- 响应 DTO：`orderId`、`orderStatus`、`scheduledStart`
- mock trace：
  - `mock-17-family-order-cancel`
  - `mock-17-family-order-reschedule`
  - `mock-17-admin-order-cancel`
  - `mock-17-order-change-empty`
  - `mock-17-order-change-error`

## 前端实现

- `frontend/src/api/stageSeventeen.ts`
  - 家属端取消、家属端改期、管理端取消。
  - 家属端操作前校验阶段6绑定：`bindingStatus=ACTIVE` 且 `scopeCodes` 包含 `ORDER_CREATE`。
  - 仅允许未服务订单状态变更：`WAIT_DISPATCH`、`DISPATCHED`、`ACCEPTED`、`ON_THE_WAY`。
  - 同步更新阶段10家属订单、阶段11/12管理端订单覆盖记录和护理任务时间/状态。
  - 写入 `order_status_log` 等价 mock 状态日志。
- `frontend/src/components/StageSeventeenOrderChangePanel.vue`
  - 家属端显示“家属改期 / 家属取消”。
  - 管理端显示“管理端取消”。
  - 两端均展示当前订单快照、最近响应 DTO 和 mock 状态。
- `frontend/src/types/stageSeventeen.ts`
  - 补充请求、响应和场景类型。
- `frontend/src/api/mockServerPaths.ts`
  - 登记阶段17三个接口路径。

## 验收结果

- 家属端改期后返回 `mock-17-family-order-reschedule`，计划时间同步为 `2026-07-10T14:00`。
- 家属端取消后返回 `mock-17-family-order-cancel`，管理端可见 `CANCELED` 和状态日志。
- 管理端取消后返回 `mock-17-admin-order-cancel`，家属端可见 `CANCELED`。
- 家属端不显示管理端取消按钮；管理端不显示家属取消/改期按钮。
- 空数据 mock 展示 `404 数据不存在`，trace 为 `mock-17-order-change-empty`。
- 错误 mock 展示 `500 服务异常`，trace 为 `mock-17-order-change-error`。

## 截图

- `docs/stage-check/stage-17-family-reschedule.png`
- `docs/stage-check/stage-17-family-cancel.png`
- `docs/stage-check/stage-17-admin-sync-after-family-cancel.png`
- `docs/stage-check/stage-17-admin-cancel.png`
- `docs/stage-check/stage-17-family-sync-after-admin-cancel.png`
- `docs/stage-check/stage-17-order-change-empty.png`
- `docs/stage-check/stage-17-order-change-error.png`

## 验证命令

- `pnpm typecheck`
- `pnpm build:h5`
- Browser：`http://127.0.0.1:5173/#/pages/family/index`、`http://127.0.0.1:5173/#/pages/admin/index`
