# 阶段34-40 成员4前端验收记录

## 范围

- 阶段34：管理端护理指标配置。
- 阶段35：管理端生成订单留档清单，护理端读取订单清单。
- 阶段36：护理端提交留档，文件先走 `POST /api/v1/files` 取得 `fileId`。
- 阶段37：管理端审核护理留档。
- 阶段38：订单指标完成校验和结果展示。
- 阶段39：护理端对未完成指标提交原因证明。
- 阶段40：管理端审核豁免证明并按结论决定是否扣分。

本记录仅声明成员4前端交付，不声明新增数据库表、后端服务或对象存储实现。

## 主要改动

- 新增 `frontend/src/types/stageThirtyFourToForty.ts`，冻结阶段34-40前端 DTO。
- 新增 `frontend/src/api/stageThirtyFourToForty.ts`，只封装真实 `/api/v1` 接口并校验响应形状。
- 新增 `frontend/src/utils/stageThirtyFourToFortyRules.ts`，集中处理中文标签、前端提交校验和审核规则。
- 新增 `frontend/src/components/StageThirtyFourToFortyAdminPanel.vue`，接入管理端指标配置、清单生成、留档审核、豁免审核。
- 新增 `frontend/src/components/StageThirtyFiveToFortyNursePanel.vue`，接入护理端订单留档、文件上传、指标校验和原因证明。
- 更新 `frontend/src/apps/admin/AdminApp.vue`，新增 `护理质控` 导航入口，按 `CARE_METRIC_CONFIG_MANAGE` 和 `CARE_EVIDENCE_REVIEW` 权限开放。
- 更新 `frontend/src/apps/nurse/NurseApp.vue`，新增 `质量留证` tab，并从任务卡进入对应订单。
- 新增 `frontend/scripts/test-stage-thirty-four-to-forty.mjs` 和 `pnpm --dir frontend test:stage34-40`。

## 契约与职责边界

- 不新增运行时 mock，不使用本地假成功回退。
- 不在前端伪造后端未返回的留档详情、证明详情或清单项。
- 护理留档只提交 `metricItemId/fileId/evidenceType/description`，文件字节、MinIO 桶名和对象路径不进入留档接口。
- 管理端审核时，`REJECTED/NEED_MORE` 留档必须填写意见。
- 豁免审核时，`APPROVED` 必须对应 `NO_DEDUCTION`，`REJECTED` 必须对应 `DEDUCT` 且必须填写意见。
- 仅护理端和管理端新增阶段34-40入口；长辈端、家属端未新增本阶段职责外页面。

## 验证命令

已执行：

```powershell
pnpm --dir frontend test:stage34-40
pnpm --dir frontend typecheck
pnpm --dir frontend build:h5
```

结果：

- `test:stage34-40`：通过，6 个子测试覆盖配置、清单、留档、审核、校验、证明和接入口。
- `typecheck`：通过。
- `build:h5`：通过，uni-app H5 构建完成。

## MCP 浏览器联调

- 重启前端 5173、用户后端 8081、护理/管理后端 8082，两个后端健康检查均 `UP` 且 `dbConnected=true`。
- `admin_demo` 登录成功，`护理质控` 菜单可见，配置、待审留档、证明复核和服务项目配置接口返回 `200`。
- `order_001` 已完成，生成清单按后端规则返回 `409 state conflict`，页面展示真实错误，不伪造成功。
- `order_031_001` 执行清单生成/读取成功，`POST /api/v1/admin/orders/order_031_001/metric-checklist/generate` 返回 `200`，页面展示 3 个真实指标。
- `nurse_reco_a_demo` 登录成功，点击对应任务的 `质量留证` 后，清单、留档、原因证明接口均返回 `200`，页面展示真实记录。

## 结论

阶段34-40成员4前端主体完成，可以进入成员3后端真实数据联调。当前实现不包含运行时 mock、不伪造后端未返回字段、不越过成员4前端职责范围。
