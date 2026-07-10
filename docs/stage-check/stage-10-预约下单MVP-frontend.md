# 阶段 10：预约下单 MVP - 前端开发日志

## 1. 阶段输入
- 总文档 PDF：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台正式开工文档第2版增强版.pdf`
- 成员4 PDF：`D:\zhimeng\Desktop\Project CSU\members\互联网智慧护理平台_成员4_四端前端负责人_开工文档第2版增强版.pdf`
- 整体设计文档：`D:\zhimeng\Desktop\Project CSU\互联网智慧护理平台完整设计文档.docx`
- 本阶段编号与名称：阶段 10，预约下单 MVP
- 本阶段涉及端：家属端
- 优先级说明：整体设计文档用于理解预约流程；接口路径、DTO、订单初始状态和阶段范围以总文档 PDF 的全局接口总契约 v2 为准。

## 2. 已完成工作
- 家属端新增“预约下单 MVP”业务面板。
- 完成长辈选择，复用阶段7 `GET /api/v1/family/elders`。
- 完成服务选择，复用阶段8 `GET /api/v1/service-items`，只使用 `ON_SHELF` 服务。
- 完成地址选择，复用阶段9 `GET /api/v1/elders/{elderId}/service-addresses`，默认选择当前默认地址。
- 完成预约时间、偏好护理员和备注输入字段。
- 完成提交预约：`POST /api/v1/family/orders`。
- 完成订单列表读取：`GET /api/v1/family/orders`。
- 完成订单详情读取：`GET /api/v1/orders/{orderId}`。
- 完成初始状态展示：`WAIT_DISPATCH`。
- 完成正常 mock、空数据 mock、错误 mock。

## 3. 文件变更
- `frontend/src/types/stageTen.ts`
- `frontend/src/api/stageTen.ts`
- `frontend/src/components/StageTenOrderPanel.vue`
- `frontend/src/components/AppSurface.vue`
- `frontend/src/api/mockServerPaths.ts`
- `frontend/src/styles/main.css`
- `frontend/src/mock/phase-10/family-orders.json`
- `frontend/src/mock/phase-10/family-orders-empty.json`
- `frontend/src/mock/phase-10/family-orders-error.json`
- `mock/phase-10/family-orders.json`
- `mock/phase-10/family-orders-empty.json`
- `mock/phase-10/family-orders-error.json`
- `docs/stage-check/stage-10-family-order.png`
- `docs/stage-check/stage-10-family-order-empty.png`
- `docs/stage-check/stage-10-family-order-error.png`
- `docs/stage-check/stage-10-预约下单MVP-frontend.md`

## 4. 使用接口
- `POST /api/v1/family/orders`
  - 请求 DTO：`{ elderId, serviceId, addressId, scheduledStart, preferredNurseId, remark }`
  - 响应 DTO：`{ orderId, orderNo, orderStatus }`
  - mock 文件：`frontend/src/mock/phase-10/family-orders.json`
- `GET /api/v1/family/orders`
  - 请求 DTO：无业务入参
  - 响应 DTO：`PageResult<{ orderId, orderNo, orderStatus }>`
  - mock 文件：`frontend/src/mock/phase-10/family-orders.json`
- `GET /api/v1/orders/{orderId}`
  - 请求 DTO：路径参数 `orderId`
  - 响应 DTO：`{ orderId, orderNo, orderStatus }`
  - mock 文件：`frontend/src/mock/phase-10/family-orders.json`

## 5. 测试记录
- 类型检查：`pnpm typecheck`，通过。
- 构建测试：`pnpm build:h5`，通过。
- 启动测试：`pnpm dev:h5`，已运行在 `http://127.0.0.1:5173/`。
- 页面访问路径：
  - 家属端：`http://127.0.0.1:5173/#/pages/family/index`
- 家属端操作测试：
  - 读取长辈、上架服务和服务地址后生成下单预览。
  - 点击提交预约后新增 `order-002`。
  - 新订单响应为 `order-002 / NO202607100002 / WAIT_DISPATCH`。
  - 订单列表出现新订单，`WAIT_DISPATCH` 数量同步增加。
  - `GET /api/v1/orders/{orderId}` 可读取同一响应 DTO。
  - 空数据 mock 返回 `records: []`，页面显示空状态。
  - 错误 mock 返回 `code=500`，页面显示 `500 服务异常`。
- 浏览器控制台：
  - 未发现阶段10业务代码 error。
  - 本地 uni 统计上报出现 HTTP 429 warning，来源为 `@dcloudio/uni-stat`，与阶段10业务逻辑无关。

## 6. 可视化验收
- 家属端主页面可打开，能看到长辈选择、服务选择、地址选择、预约时间、下单预览、订单列表、接口路径和响应 DTO。
- 关键按钮可点击：提交预约、读取详情、重置 mock、空数据 mock、错误 mock。
- 状态变化可见：提交后出现新订单，状态固定为 `WAIT_DISPATCH`。
- 截图文件：
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-10-family-order.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-10-family-order-empty.png`
  - `D:\zhimeng\Desktop\Project CSU\smart-nursing-platform\docs\stage-check\stage-10-family-order-error.png`

## 7. 问题与处理
- 问题：阶段10必须校验 ACTIVE 绑定、ORDER_CREATE scope、服务上架和地址归属。
  - 处理：mock 提交前组合调用阶段6、阶段8、阶段9契约数据；不满足时返回固定错误码。
- 问题：阶段11才做管理端订单列表。
  - 处理：阶段10只在家属端展示本人预约订单，不向管理端接入订单列表。
- 问题：响应 DTO 只允许 `{ orderId, orderNo, orderStatus }`。
  - 处理：请求字段只在下单表单和 POST 中使用；列表、详情和提交响应严格展示 PDF 锁定 DTO。
- 问题：后端阶段10接口当前未在前端直接联通验证。
  - 处理：使用同路径、同字段、同初始状态的契约 mock；请求层已支持后续切换真实接口。

## 8. 阶段完成结论
- 是否完成本阶段：是。
- 是否满足 PDF 中测试完成检验：是，下单后前端订单列表出现新订单，状态为 `WAIT_DISPATCH`。
- 是否可以进入下一阶段：是。
- 未完成项：无前端阻塞项；真实后端联调需等待成员3对应接口可用后切换 `VITE_USE_MOCK=false` 验证。
