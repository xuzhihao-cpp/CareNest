# 阶段 4 验收记录：四端首页占位看板（前端）

## 阶段目标

- 每端首页展示本端关键入口。
- 使用 `mock/home/*.json` 可视化业务骨架。
- 首页字段固定为 `{cards,quickActions,todoCount}`。
- 按钮可进入对应空详情或列表占位区。

## 接口契约

阶段 4 前端只调用并模拟以下 PDF 指定接口：

- `GET /api/v1/elder/home-summary`
- `GET /api/v1/family/home-summary`
- `GET /api/v1/nurse/workbench-summary`
- `GET /api/v1/admin/dashboard/overview`

统一响应结构仍为：

```json
{"code":0,"message":"success","data":{"cards":[],"quickActions":[],"todoCount":0},"traceId":"mock-4"}
```

## 本次前端改动

- 新增阶段 4 类型与接口模拟：`frontend/src/types/stageFour.ts`、`frontend/src/api/stageFour.ts`。
- 新增首页 mock：
  - `mock/home/elder-home-summary.json`
  - `mock/home/family-home-summary.json`
  - `mock/home/nurse-workbench-summary.json`
  - `mock/home/admin-dashboard-overview.json`
- 前端内置同结构 mock：`frontend/src/mock/home/*.json`。
- 四端壳 `frontend/src/components/AppSurface.vue` 增加：
  - 当前端对应首页 summary endpoint 展示。
  - `cards` 渲染关键卡片。
  - `todoCount` 展示待办数量。
  - `quickActions` 按 `permissionCode` 过滤并渲染快捷入口。
  - 点击快捷入口进入空列表/详情占位区。
- `docs/api/phase-04-api.md` 与 `docs/dictionary/data-dictionary.md` 已同步阶段 4 字段和接口示例。

## 测试结果

| 项目 | 结果 |
| --- | --- |
| `pnpm typecheck` | 通过 |
| `pnpm build:h5` | 通过 |
| 长辈端首页 endpoint | 可见 |
| 家属端首页 endpoint | 可见 |
| 护理端首页 endpoint | 可见 |
| 管理端首页 endpoint | 可见 |
| 四端首页卡片 | 每端 3 张 |
| 四端快捷入口 | 每端 3 个 |
| 快捷入口点击 | 显示空列表/详情占位区 |
| Console error | 无 |

## 截图证据

- `docs/stage-check/stage-04-elder.png`
- `docs/stage-check/stage-04-family.png`
- `docs/stage-check/stage-04-nurse.png`
- `docs/stage-check/stage-04-admin.png`

## 环境说明

- `pnpm dev:h5` 按项目约束仍配置为 3000。
- 本次浏览器验收时，Windows 临时拒绝监听 `127.0.0.1:3000`，因此使用已构建的 H5 静态产物在 `127.0.0.1:4173` 进行截图验收；构建产物来自同一次 `pnpm build:h5`。

## 结论

阶段 4 前端 MVP 已完成。当前实现仍为 mock 模式，接口路径、统一响应结构、首页 DTO、权限过滤和角色枚举均已与 PDF/文档同步；后端真实首页接口完成后可按相同 DTO 替换 mock。

