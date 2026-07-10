# 阶段 6：长辈/家属多对多绑定 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 6，长辈/家属多对多绑定 MVP
- 本阶段涉及端：家属端、长辈端
- 优先级说明：整体设计文档用于理解多对多绑定、授权边界、操作留痕和适老化确认体验；接口路径、DTO、状态枚举和授权范围以总文档 PDF 的全局接口总契约 v2 为准。

## 2. 已完成工作
- 家属端新增“长辈/家属多对多绑定 MVP”业务面板。
- 长辈端新增待确认绑定卡片和确认绑定操作。
- 完成绑定申请表单：`elderInviteCode`、`relationType`、`scopeCodes`。
- 完成绑定列表展示：`bindingId`、`elderId`、`elderName`、`relationType`、`bindingStatus`、`scopeCodes`。
- 完成状态展示：`PENDING`、`ACTIVE`、`REVOKED`，并保留固定枚举扩展 `REJECTED`、`EXPIRED`。
- 完成操作交互：提交绑定、长辈确认、更新授权范围、撤销授权。
- 完成正常 mock、空数据 mock、错误 mock。
- 完成最近一次操作响应 DTO 展示，展示 `code / message / traceId` 和核心响应字段。

## 3. 文件变更
- `frontend/src/types/stageSix.ts`
- `frontend/src/api/stageSix.ts`
- `frontend/src/components/StageSixBindingPanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-06/family-bindings.json`
- `frontend/src/mock/phase-06/family-bindings-empty.json`
- `frontend/src/mock/phase-06/family-bindings-error.json`
- `mock/phase-06/family-bindings.json`
- `mock/phase-06/family-bindings-empty.json`
- `mock/phase-06/family-bindings-error.json`
- `docs/stage-check/stage-06-binding-family.png`
- `docs/stage-check/stage-06-binding-family-empty.png`
- `docs/stage-check/stage-06-binding-family-error.png`
- `docs/stage-check/stage-06-binding-elder.png`
- `docs/stage-check/stage-06-长辈家属多对多绑定MVP-frontend.md`

## 4. 使用接口
- `POST /api/v1/family/bindings`
  - 请求 DTO：`{ elderInviteCode, relationType, scopeCodes }`
  - 响应 DTO：`{ bindingId, elderId, elderName, relationType, bindingStatus, scopeCodes }`
  - mock 文件：`frontend/src/mock/phase-06/family-bindings.json`
- `GET /api/v1/family/bindings`
  - 请求 DTO：无业务入参
  - 响应 DTO：`{ records, total, page, size }`，`records[]` 为绑定响应 DTO
  - mock 文件：`frontend/src/mock/phase-06/family-bindings.json`
- `POST /api/v1/elder/bindings/{bindingId}/approve`
  - 请求 DTO：路径参数 `bindingId`
  - 响应 DTO：`{ bindingId, elderId, elderName, relationType, bindingStatus, scopeCodes }`
  - mock 文件：`frontend/src/mock/phase-06/family-bindings.json`
- `PUT /api/v1/family/bindings/{bindingId}/scopes`
  - 请求 DTO：`{ elderInviteCode, relationType, scopeCodes }`
  - 响应 DTO：`{ bindingId, elderId, elderName, relationType, bindingStatus, scopeCodes }`
  - mock 文件：`frontend/src/mock/phase-06/family-bindings.json`
- `POST /api/v1/family/bindings/{bindingId}/revoke`
  - 请求 DTO：`{ elderInviteCode, relationType, scopeCodes }`
  - 响应 DTO：`{ bindingId, elderId, elderName, relationType, bindingStatus, scopeCodes }`
  - mock 文件：`frontend/src/mock/phase-06/family-bindings.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已启动到 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 家属端：`http://127.0.0.1:5173/#/pages/family/index`
  - 长辈端：`http://127.0.0.1:5173/#/pages/elder/index`
- 家属端操作测试：
  - 提交绑定后新增 `PENDING` 记录，成功返回 `code=0`。
  - 更新授权后 `scopeCodes` 增加 `ARCHIVE_EDIT`，成功返回 `code=0`。
  - 空数据 mock 返回 `records: []`，页面显示空状态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 长辈端操作测试：
  - 点击确认绑定后 `bindingStatus` 从 `PENDING` 更新为 `ACTIVE`。
- 权限/角色测试：
  - 家属端使用 `FAMILY` 登录，绑定申请、列表、授权更新和撤销可见。
  - 长辈端使用 `ELDER` 登录，确认绑定入口可见。
- 浏览器控制台：未发现本地应用 error/warning。

## 6. 可视化验收
- 家属端主页面可打开，能看到绑定表单、绑定列表、状态标签、接口路径和响应 DTO。
- 长辈端页面可打开，能看到待确认绑定卡片和确认按钮。
- 关键按钮可点击：提交绑定、更新授权、撤销授权、空数据 mock、错误 mock、确认绑定。
- 状态变化可见：`PENDING` 新增、`ACTIVE` 确认、`REVOKED` 撤销、`500` 错误。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-06-binding-family.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-06-binding-family-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-06-binding-family-error.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-06-binding-elder.png`

## 7. 问题与处理
- 问题：PowerShell 初始输出未设置 UTF-8，导致文档和代码内容显示乱码。
  - 处理：切换 `[Console]::OutputEncoding` 与 `$OutputEncoding` 为 UTF-8 后重新读取，确认文件本身为正常 UTF-8。
- 问题：完整设计文档提到“投诉评价”等更泛化授权范围，以及“已绑定/已解除/已冻结”等业务表述。
  - 处理：阶段6严格使用总文档 PDF 的固定 `scopeCodes` 与 `bindingStatus`；未新增 PDF 外字段、状态或接口。
- 问题：后端阶段6接口当前未在前端直接联通验证。
  - 处理：使用同路径、同字段、同状态的契约 mock；请求层已支持后续切换真实接口。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，前端可展示一个家属管理多个长辈、长辈确认绑定、授权范围修改和撤销状态。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员2对应接口可用后切换 `VITE_USE_MOCK=false` 验证。

