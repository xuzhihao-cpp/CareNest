# 阶段15 服务报告 MVP 前端验收

## 依据

- 参考整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 冲突时以正式开工文档为准：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 阶段15范围：服务报告生成与查看。
- 本阶段不包含阶段16的报告确认、确认记录归档或 ACK 流程。

## 接口与数据

- `POST /api/v1/orders/{orderId}/service-report/generate`
- `GET /api/v1/orders/{orderId}/service-report`
- 响应 DTO：`reportId`、`orderId`、`summary`、`vitalSigns`、`serviceRecords`、`nursingAdvice`
- mock trace：
  - `mock-15-service-report-generate`
  - `mock-15-service-report`
  - `mock-15-service-report-empty`
  - `mock-15-service-report-error`

## 前端实现

- `frontend/src/api/stageFifteen.ts`
  - 读取阶段14护理执行记录，生成阶段15服务报告。
  - 报告写入本地 mock 存储，后续家属端、长辈端读取同一份报告。
- `frontend/src/components/StageFifteenServiceReportPanel.vue`
  - 管理端、护理端可生成和读取报告。
  - 家属端、长辈端只读查看报告。
  - 提供正常、空数据、错误 mock 切换。
- `frontend/src/api/mockServerPaths.ts`
  - 补充阶段15两个接口路径。
- `frontend/src/styles/main.css`
  - 补充阶段15报告面板、接口区、报告内容区响应式样式。

## 验收结果

- 管理端已通过 `order-002` 生成 `report-001`。
- 家属端可查看同一份 `report-001 / order-002` 报告。
- 长辈端可查看同一份 `report-001 / order-002` 报告。
- 家属端、长辈端未出现“生成报告”按钮。
- 空数据 mock 展示 `404 数据不存在`，trace 为 `mock-15-service-report-empty`。
- 错误 mock 展示 `500 服务异常`，trace 为 `mock-15-service-report-error`。

## 截图

- `docs/stage-check/stage-15-admin-report-generate.png`
- `docs/stage-check/stage-15-family-report-view.png`
- `docs/stage-check/stage-15-elder-report-view.png`
- `docs/stage-check/stage-15-report-empty.png`
- `docs/stage-check/stage-15-report-error.png`

## 验证命令

- `pnpm typecheck`
- `pnpm build:h5`
- Browser：`http://127.0.0.1:5173/#/pages/admin/index`
