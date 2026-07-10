# 阶段 9：服务地址 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 9，服务地址 MVP
- 本阶段涉及端：家属端
- 优先级说明：整体设计文档用于理解预约地址和上门服务场景；接口路径、DTO、字段和阶段范围以总文档 PDF 的全局接口总契约 v2 为准。

## 2. 已完成工作
- 家属端新增“服务地址 MVP”业务面板。
- 完成按长辈切换服务地址列表。
- 完成新增地址：`POST /api/v1/elders/{elderId}/service-addresses`。
- 完成更新地址和设置默认地址：`PUT /api/v1/service-addresses/{addressId}`。
- 完成删除地址：`DELETE /api/v1/service-addresses/{addressId}`。
- 完成字段：`contactName`、`contactPhone`、`regionCode`、`detailAddress`、`isDefault`。
- 完成响应 DTO 展示：`addressId`、`fullAddress`、`isDefault`。
- 完成“预约页地址选择预览”，用于验证默认地址可被预约流程读取；不提前创建阶段10订单。
- 完成正常 mock、空数据 mock、错误 mock。

## 3. 文件变更
- `frontend/src/types/stageNine.ts`
- `frontend/src/api/stageNine.ts`
- `frontend/src/components/StageNineServiceAddressPanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-09/service-addresses.json`
- `frontend/src/mock/phase-09/service-addresses-empty.json`
- `frontend/src/mock/phase-09/service-addresses-error.json`
- `mock/phase-09/service-addresses.json`
- `mock/phase-09/service-addresses-empty.json`
- `mock/phase-09/service-addresses-error.json`
- `docs/stage-check/stage-09-service-address-family.png`
- `docs/stage-check/stage-09-service-address-family-empty.png`
- `docs/stage-check/stage-09-service-address-family-error.png`
- `docs/stage-check/stage-09-服务地址MVP-frontend.md`

## 4. 使用接口
- `GET /api/v1/elders/{elderId}/service-addresses`
  - 请求 DTO：路径参数 `elderId`
  - 响应 DTO：`PageResult<{ addressId, fullAddress, isDefault }>`
  - mock 文件：`frontend/src/mock/phase-09/service-addresses.json`
- `POST /api/v1/elders/{elderId}/service-addresses`
  - 请求 DTO：`{ contactName, contactPhone, regionCode, detailAddress, isDefault }`
  - 响应 DTO：`{ addressId, fullAddress, isDefault }`
  - mock 文件：`frontend/src/mock/phase-09/service-addresses.json`
- `PUT /api/v1/service-addresses/{addressId}`
  - 请求 DTO：`{ contactName, contactPhone, regionCode, detailAddress, isDefault }`
  - 响应 DTO：`{ addressId, fullAddress, isDefault }`
  - mock 文件：`frontend/src/mock/phase-09/service-addresses.json`
- `DELETE /api/v1/service-addresses/{addressId}`
  - 请求 DTO：路径参数 `addressId`
  - 响应 DTO：`{ addressId, fullAddress, isDefault }`
  - mock 文件：`frontend/src/mock/phase-09/service-addresses.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已运行在 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 家属端：`http://127.0.0.1:5173/#/pages/family/index`
- 家属端操作测试：
  - 读取张奶奶地址列表，初始返回 2 条地址，默认地址为 `address-001`。
  - 新增地址后返回 `address-004`，并设置为默认地址。
  - 新增默认地址后旧默认地址自动变为备用地址，满足“同一长辈只能有一个默认地址”。
  - 预约页地址选择预览自动选择当前默认地址 `address-004`。
  - 表单必填校验触发 `422 业务规则不满足`，固定错误码可见。
  - 空数据 mock 返回 `records: []`，页面显示空状态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 浏览器控制台：未发现本地应用 error/warning。

## 6. 可视化验收
- 家属端主页面可打开，能看到长辈切换、地址列表、地址表单、默认地址标识、预约地址预览、接口路径和响应 DTO。
- 关键按钮可点击：新增地址、保存所选、设为默认、删除、重置 mock、空数据 mock、错误 mock。
- 状态变化可见：新增默认地址后列表总数增加，默认地址唯一，预约预览选择默认地址。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-09-service-address-family.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-09-service-address-family-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-09-service-address-family-error.png`

## 7. 问题与处理
- 问题：测试完成检验提到“预约页可选择默认地址”，但阶段10才创建预约订单。
  - 处理：阶段9只实现预约地址选择预览，不调用订单接口、不生成订单。
- 问题：响应 DTO 只允许 `{ addressId, fullAddress, isDefault }`，但表单需要联系人、电话和详细地址。
  - 处理：请求字段只在表单和 POST/PUT 中使用；列表和操作响应严格展示 PDF 锁定 DTO。
- 问题：浏览器自动化输入通道在一次测试中触发虚拟剪贴板错误。
  - 处理：改用已有表单值和按钮流程完成 POST/默认地址规则验证，并额外记录 422 表单校验状态。
- 问题：后端阶段9接口当前未在前端直接联通验证。
  - 处理：使用同路径、同字段、同状态的契约 mock；请求层已支持后续切换真实接口。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，家属端可维护服务地址，默认地址可在预约预览中被选择。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员2对应接口可用后切换 `VITE_USE_MOCK=false` 验证。
