# 阶段 7：长辈基础档案 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 7，长辈基础档案 MVP
- 本阶段涉及端：家属端、长辈端
- 优先级说明：整体设计文档用于理解健康档案、授权边界、操作留痕和长辈/家属身份分离；接口路径、DTO、枚举和阶段范围以总文档 PDF 的全局接口总契约 v2 为准。

## 2. 已完成工作
- 家属端新增“长辈基础档案 MVP”业务面板。
- 长辈端新增基础档案只读卡片和紧急联系人展示。
- 完成家属长辈列表：`GET /api/v1/family/elders`。
- 完成单个长辈档案读取：`GET /api/v1/elders/{elderId}/profile`。
- 完成基础档案保存：`PUT /api/v1/elders/{elderId}/profile`。
- 完成表单字段：`name`、`gender`、`birthDate`、`careLevel`、`emergencyContacts`。
- 完成枚举展示：`MALE`、`FEMALE`、`UNKNOWN`；`LEVEL_1`、`LEVEL_2`、`LEVEL_3`；`SON`、`DAUGHTER`、`SPOUSE`、`OTHER`。
- 完成正常 mock、空数据 mock、错误 mock。
- 完成最近一次保存响应 DTO 展示，展示 `code / message / traceId`、`elderId` 和 `profileVersion`。

## 3. 文件变更
- `frontend/src/types/stageSeven.ts`
- `frontend/src/api/stageSeven.ts`
- `frontend/src/components/StageSevenProfilePanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-07/family-elders.json`
- `frontend/src/mock/phase-07/family-elders-empty.json`
- `frontend/src/mock/phase-07/family-elders-error.json`
- `mock/phase-07/family-elders.json`
- `mock/phase-07/family-elders-empty.json`
- `mock/phase-07/family-elders-error.json`
- `docs/stage-check/stage-07-elder-profile-family.png`
- `docs/stage-check/stage-07-elder-profile-family-empty.png`
- `docs/stage-check/stage-07-elder-profile-family-error.png`
- `docs/stage-check/stage-07-elder-profile-elder.png`
- `docs/stage-check/stage-07-长辈基础档案MVP-frontend.md`

## 4. 使用接口
- `GET /api/v1/family/elders`
  - 请求 DTO：无业务入参
  - 响应 DTO：`{ records, total, page, size }`，`records[]` 为 `ElderProfileDetail`
  - mock 文件：`frontend/src/mock/phase-07/family-elders.json`
- `GET /api/v1/elders/{elderId}/profile`
  - 请求 DTO：路径参数 `elderId`
  - 响应 DTO：`{ elderId, profileVersion, name, gender, birthDate, careLevel, emergencyContacts }`
  - mock 文件：`frontend/src/mock/phase-07/family-elders.json`
- `PUT /api/v1/elders/{elderId}/profile`
  - 请求 DTO：`{ name, gender, birthDate, careLevel, emergencyContacts }`
  - 响应 DTO：`{ elderId, profileVersion }`
  - mock 文件：`frontend/src/mock/phase-07/family-elders.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已运行在 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 家属端：`http://127.0.0.1:5173/#/pages/family/index`
  - 长辈端：`http://127.0.0.1:5173/#/pages/elder/index`
- 家属端操作测试：
  - 读取家属绑定的长辈列表，返回 2 条基础档案。
  - 点击保存基础档案后，`profileVersion` 从 `4` 递增到 `5`，列表、摘要和保存响应 DTO 同步显示。
  - 空数据 mock 返回 `records: []`，页面显示空状态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 长辈端操作测试：
  - 使用 `ELDER` 登录后可读取本人基础档案和紧急联系人。
  - 长辈端不展示保存按钮，保持只读。
- 浏览器控制台：未发现本地应用 error/warning。

## 6. 可视化验收
- 家属端主页面可打开，能看到长辈列表、基础档案表单、紧急联系人、接口路径和保存响应 DTO。
- 长辈端页面可打开，能看到只读档案卡片和紧急联系人列表。
- 关键按钮可点击：保存基础档案、正常 mock、空数据 mock、错误 mock。
- 状态变化可见：`profileVersion` 递增、空数据状态、`500` 错误。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-07-elder-profile-family.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-07-elder-profile-family-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-07-elder-profile-family-error.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-07-elder-profile-elder.png`

## 7. 问题与处理
- 问题：整体设计文档中健康档案范围更宽，包含后续慢病、用药、过敏、风险标签等内容。
  - 处理：阶段7严格只实现总文档 PDF 指定的基础档案字段；慢病、用药、过敏和风险标签留到后续健康档案增强阶段。
- 问题：初次保存后响应 DTO 已返回新 `profileVersion`，但家属端列表仍显示旧版本。
  - 处理：保存后读取档案并同步替换当前列表记录，确保列表、摘要和响应 DTO 三处版本一致。
- 问题：管理端不是本阶段范围。
  - 处理：未向管理端或护理端接入阶段7业务面板；管理端继续保持电脑网页 PC Web 管理工作台形态。
- 问题：后端阶段7接口当前未在前端直接联通验证。
  - 处理：使用同路径、同字段、同枚举的契约 mock；请求层已支持后续切换真实接口。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，保存后刷新读取仍能看到基础档案，且 `profileVersion` 已递增展示。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员2对应接口可用后切换 `VITE_USE_MOCK=false` 验证。
