# 阶段 12：派单与护理任务状态 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 12，派单与护理任务状态 MVP
- 本阶段涉及端：管理端、护理端
- 端类型说明：管理端是电脑网页工作台；护理端是移动端任务执行界面。
- 优先级说明：整体设计文档用于理解派单和护理任务流；若与正式开工 PDF 冲突，以正式开工 PDF 为准。

## 2. 已完成工作
- 管理端新增“派单与任务状态 MVP”派单工作台。
- 护理端新增同一阶段任务面板，展示管理端派单生成的 `nurse_task`。
- 完成管理端派单接口契约：`POST /api/v1/admin/orders/{orderId}/dispatch`。
- 完成护理端接单接口契约：`POST /api/v1/nurse/tasks/{taskId}/accept`。
- 完成护理端任务状态推进接口契约：`POST /api/v1/nurse/tasks/{taskId}/status`。
- 派单后生成 `taskId`，订单状态由 `WAIT_DISPATCH` 同步为 `DISPATCHED`。
- 护理端接单后任务和订单状态同步为 `ACCEPTED`。
- 护理端状态推进支持 `ON_THE_WAY`、`SERVING`，不进入阶段 14 服务记录。
- 阶段 11 管理端订单列表可读取阶段 12 状态覆盖，按“服务中”筛出同一订单。
- 支持正常 mock、空数据 mock、错误 mock。

## 3. 文件变更
- `frontend/src/types/stageTwelve.ts`
- `frontend/src/api/stageTwelve.ts`
- `frontend/src/components/StageTwelveDispatchPanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/stageEleven.ts`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-12/dispatch-response.json`
- `frontend/src/mock/phase-12/nurse-tasks.json`
- `frontend/src/mock/phase-12/nurse-tasks-empty.json`
- `frontend/src/mock/phase-12/nurse-tasks-error.json`
- `mock/phase-12/dispatch-response.json`
- `mock/phase-12/nurse-tasks.json`
- `mock/phase-12/nurse-tasks-empty.json`
- `mock/phase-12/nurse-tasks-error.json`
- `docs/stage-check/stage-12-admin-dispatch.png`
- `docs/stage-check/stage-12-nurse-task-visible.png`
- `docs/stage-check/stage-12-nurse-accepted.png`
- `docs/stage-check/stage-12-nurse-serving.png`
- `docs/stage-check/stage-12-nurse-empty.png`
- `docs/stage-check/stage-12-nurse-error.png`
- `docs/stage-check/stage-12-admin-order-synced.png`
- `docs/stage-check/stage-12-派单与护理任务状态MVP-frontend.md`

## 4. 使用接口
- `POST /api/v1/admin/orders/{orderId}/dispatch`
  - 请求 DTO：`{ nurseId, dispatchRemark, targetStatus }`
  - 响应 DTO：`{ orderId, orderNo, orderStatus, taskId }`
  - mock 文件：`frontend/src/mock/phase-12/dispatch-response.json`
- `POST /api/v1/nurse/tasks/{taskId}/accept`
  - 请求 DTO：`{ nurseId, dispatchRemark, targetStatus }`
  - 响应 DTO：`{ orderId, orderNo, orderStatus, taskId }`
  - mock 文件：`frontend/src/mock/phase-12/dispatch-response.json`
- `POST /api/v1/nurse/tasks/{taskId}/status`
  - 请求 DTO：`{ nurseId, dispatchRemark, targetStatus }`
  - 响应 DTO：`{ orderId, orderNo, orderStatus, taskId }`
  - mock 文件：`frontend/src/mock/phase-12/dispatch-response.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已恢复运行在 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 管理端：`http://127.0.0.1:5173/#/pages/admin/index`
  - 护理端：`http://127.0.0.1:5173/#/pages/nurse/index`
- 管理端操作测试：
  - 使用 `ADMIN` 管理员演示账号。
  - 阶段 12 面板列出阶段 11 的 `WAIT_DISPATCH` 订单。
  - 点击“确认派单”后生成 `task-001`。
  - 响应 DTO 返回 `order-002 / DISPATCHED / task-001`。
- 护理端操作测试：
  - 使用 `NURSE` 护理演示账号。
  - 护理端能看到管理端刚派出的 `NO202607100002 · task-001`。
  - 点击“接单”后响应 `mock-12-accept`，任务状态和订单状态同步为 `ACCEPTED`。
  - 点击“出发”“开始服务”后响应 `mock-12-status`，任务状态和订单状态同步为 `SERVING`。
  - 空数据 mock 返回 `records: []`，页面进入空态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 状态回看测试：
  - 切回管理端后，阶段 12 面板显示当前任务：`order-002 / SERVING`。
  - 阶段 11 订单列表按“服务中”筛选命中 `NO202607100002`。
- 浏览器控制台：
  - 阶段 12 业务流程未发现 error。

## 6. 可视化验收
- 管理端电脑网页可完成派单，并显示 `POST /api/v1/admin/orders/{orderId}/dispatch`。
- 护理端移动端任务面板可看到派单任务、接单、出发和开始服务。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-admin-dispatch.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-nurse-task-visible.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-nurse-accepted.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-nurse-serving.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-nurse-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-nurse-error.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-12-admin-order-synced.png`

## 7. 问题与处理
- 问题：阶段 12 完成标准要求派单后护理端能看到任务，且订单状态同步变化。
  - 处理：新增阶段 12 本地 mock 状态，派单写入任务视图和订单覆盖视图；阶段 11 管理端订单列表读取覆盖状态。
- 问题：阶段 13 才是护理任务 GET 列表契约。
  - 处理：阶段 12 护理端列表只作为派单结果可视化验收，不把 `GET /api/v1/nurse/tasks` 登记为阶段 12 正式接口。
- 问题：阶段 14 才进入服务记录、生命体征和报告生成前置。
  - 处理：阶段 12 只推进到 `SERVING`，不提交服务记录、不生成报告。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，派单后护理端能看到任务，订单状态同步变化。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员3对应接口可用后切换 `VITE_USE_MOCK=false` 验证。
