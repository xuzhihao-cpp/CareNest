# 阶段16 长辈/家属确认 MVP 前端验收

## 依据

- 参考整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 冲突时以正式开工文档为准：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 阶段16范围：长辈确认体验，家属确认报告和档案变更建议。
- 前置依赖：阶段15服务报告。

## 接口与 DTO

- `POST /api/v1/elder/reports/{reportId}/ack`
- `POST /api/v1/family/reports/{reportId}/ack`
- `POST /api/v1/family/reports/{reportId}/archive-suggestions/decision`
- 请求 DTO：`ackResult`、`satisfaction`、`remark`、`acceptedSuggestionIds`
- 响应 DTO：`ackId`、`ackResult`、`reportStatus`
- mock trace：
  - `mock-16-elder-report-ack`
  - `mock-16-family-report-ack`
  - `mock-16-archive-suggestions-decision`
  - `mock-16-report-ack-empty`
  - `mock-16-report-ack-error`

## 前端实现

- `frontend/src/api/stageSixteen.ts`
  - 按角色处理长辈端、家属端确认。
  - 家属端确认前校验阶段6绑定：`bindingStatus=ACTIVE` 且 `scopeCodes` 包含 `REPORT_CONFIRM`。
  - 写入 `care_report_ack` 等价 mock 存储。
  - 家属档案建议决策后生成 `health_info_review_task` 等价 mock 存储。
  - 确认通过后同步订单状态为 `COMPLETED`；异议时回到 `WAIT_CONFIRM`。
- `frontend/src/components/StageSixteenReportAckPanel.vue`
  - 长辈端显示“长辈确认报告 / 长辈提出异议”。
  - 家属端显示“家属确认报告 / 家属提出异议 / 档案建议确认”。
  - 管理端、护理端只观察状态同步，不显示确认按钮。
- `frontend/src/types/stageSixteen.ts`
  - 补充阶段16请求、响应、确认记录、健康审核任务类型。
- `frontend/src/api/mockServerPaths.ts`
  - 登记阶段16三个接口路径。

## 验收结果

- 家属端确认报告后返回 `ackId`，`reportStatus=CONFIRMED`，订单状态同步为 `COMPLETED`。
- 家属端仅在存在 `ACTIVE + REPORT_CONFIRM` 授权绑定时可确认；撤销绑定后确认返回 `403 无权限`，trace 为 `mock-16-family-binding-scope-forbidden`。
- 家属端确认档案建议后返回 `reportStatus=ARCHIVE_PENDING`，并生成 `health_info_review_task` 待审核任务。
- 长辈端可确认同一报告，返回 `mock-16-elder-report-ack`，订单状态保持 `COMPLETED`。
- 管理端可观察阶段16确认记录和状态同步结果，但不显示确认按钮。
- 空数据 mock 展示 `404 数据不存在`，trace 为 `mock-16-report-ack-empty`。
- 错误 mock 展示 `500 服务异常`，trace 为 `mock-16-report-ack-error`。

## 截图

- `docs/stage-check/stage-16-family-report-ack.png`
- `docs/stage-check/stage-16-family-scope-pass.png`
- `docs/stage-check/stage-16-family-scope-forbidden.png`
- `docs/stage-check/stage-16-family-archive-decision.png`
- `docs/stage-check/stage-16-elder-report-ack.png`
- `docs/stage-check/stage-16-admin-status-sync.png`
- `docs/stage-check/stage-16-report-ack-empty.png`
- `docs/stage-check/stage-16-report-ack-error.png`

## 验证命令

- `pnpm typecheck`
- `pnpm build:h5`
- Browser：`http://127.0.0.1:5173/#/pages/admin/index`
