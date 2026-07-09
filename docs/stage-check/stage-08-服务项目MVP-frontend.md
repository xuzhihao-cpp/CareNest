# 阶段 8：服务项目 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 8，服务项目 MVP
- 本阶段涉及端：管理端、家属端
- 优先级说明：整体设计文档用于理解服务项目商品化配置；接口路径、DTO、状态和阶段范围以总文档 PDF 的全局接口总契约 v2 为准。

## 2. 已完成工作
- 管理端新增“服务项目 MVP”维护面板，并保持电脑网页 PC Web 工作台形态。
- 家属端新增可预约服务列表，只展示 `ON_SHELF` 服务。
- 完成服务列表读取：`GET /api/v1/service-items`。
- 完成单个服务读取契约：`GET /api/v1/service-items/{serviceId}`。
- 完成管理端新增服务：`POST /api/v1/admin/service-items`。
- 完成管理端保存和上下架：`PUT /api/v1/admin/service-items/{serviceId}`。
- 完成字段：`serviceName`、`category`、`price`、`durationMinutes`、`status`。
- 完成状态：`ON_SHELF`、`OFF_SHELF`。
- 完成正常 mock、空数据 mock、错误 mock。
- 完成最近一次服务项目响应 DTO 展示。

## 3. 文件变更
- `frontend/src/types/stageEight.ts`
- `frontend/src/api/stageEight.ts`
- `frontend/src/components/StageEightServiceItemsPanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-08/service-items.json`
- `frontend/src/mock/phase-08/service-items-empty.json`
- `frontend/src/mock/phase-08/service-items-error.json`
- `mock/phase-08/service-items.json`
- `mock/phase-08/service-items-empty.json`
- `mock/phase-08/service-items-error.json`
- `docs/stage-check/stage-08-service-items-admin.png`
- `docs/stage-check/stage-08-service-items-family.png`
- `docs/stage-check/stage-08-service-items-family-empty.png`
- `docs/stage-check/stage-08-service-items-family-error.png`
- `docs/stage-check/stage-08-服务项目MVP-frontend.md`

## 4. 使用接口
- `GET /api/v1/service-items`
  - 请求 DTO：无业务入参
  - 响应 DTO：前端列表使用 `PageResult<ServiceItemResponse>`，`records[]` 为服务项目响应 DTO
  - mock 文件：`frontend/src/mock/phase-08/service-items.json`
- `GET /api/v1/service-items/{serviceId}`
  - 请求 DTO：路径参数 `serviceId`
  - 响应 DTO：`{ serviceId, serviceName, category, price, durationMinutes, status }`
  - mock 文件：`frontend/src/mock/phase-08/service-items.json`
- `POST /api/v1/admin/service-items`
  - 请求 DTO：`{ serviceName, category, price, durationMinutes, status }`
  - 响应 DTO：`{ serviceId, serviceName, category, price, durationMinutes, status }`
  - mock 文件：`frontend/src/mock/phase-08/service-items.json`
- `PUT /api/v1/admin/service-items/{serviceId}`
  - 请求 DTO：`{ serviceName, category, price, durationMinutes, status }`
  - 响应 DTO：`{ serviceId, serviceName, category, price, durationMinutes, status }`
  - mock 文件：`frontend/src/mock/phase-08/service-items.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已运行在 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 管理端：`http://127.0.0.1:5173/#/pages/admin/index`
  - 家属端：`http://127.0.0.1:5173/#/pages/family/index`
- 管理端操作测试：
  - 读取全部服务项目，包含 `ON_SHELF` 和 `OFF_SHELF`。
  - 新增 `陪诊协助`，返回 `service-004`，状态为 `ON_SHELF`。
  - 管理端页面显示最近一次响应 `mock-8-service-create`。
- 家属端操作测试：
  - 登录家属端后可见新增的 `陪诊协助`，价格 `¥239`，时长 `75` 分钟。
  - 家属端不显示 `OFF_SHELF` 的 `夜间照护`。
  - 空数据 mock 返回 `records: []`，页面显示空状态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 浏览器控制台：未发现本地应用 error/warning。

## 6. 可视化验收
- 管理端主页面可打开，能看到服务项目表单、服务项目列表、上下架按钮、接口路径和响应 DTO。
- 家属端主页面可打开，能看到可预约服务列表，并只展示 `ON_SHELF` 服务。
- 关键按钮可点击：新增服务、保存所选、上下架、正常 mock、空数据 mock、错误 mock。
- 状态变化可见：管理端新增服务后，家属端服务列表出现该服务。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-08-service-items-admin.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-08-service-items-family.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-08-service-items-family-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-08-service-items-family-error.png`

## 7. 问题与处理
- 问题：整体设计文档提到服务说明、适用人群、注意事项等更完整商品配置。
  - 处理：阶段8严格只实现总文档 PDF 指定字段；不新增未列字段。
- 问题：`GET /api/v1/service-items` 是列表读取，但 PDF 响应 DTO 行列的是单个服务项目字段。
  - 处理：前端列表 mock 使用 `PageResult<ServiceItemResponse>`，单项、新增、更新均严格使用 PDF 锁定 DTO。
- 问题：管理端形态必须是电脑网页。
  - 处理：阶段8管理端面板接入现有 PC Web 宽屏工作台，未按移动端折叠为手机页面。
- 问题：后端阶段8接口当前未在前端直接联通验证。
  - 处理：使用同路径、同字段、同状态的契约 mock；请求层已支持后续切换真实接口。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，管理端新增服务后，家属端服务列表出现。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员3对应接口可用后切换 `VITE_USE_MOCK=false` 验证。
