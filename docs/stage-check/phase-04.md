# Phase 04 验收记录：四端首页占位看板

## 阶段目标

每端首页展示本端关键入口，先用 mock 可视化业务骨架。

## 验收清单

- [x] `GET /api/v1/elder/home-summary` 返回 `{cards,quickActions,todoCount}`。
- [x] `GET /api/v1/family/home-summary` 返回 `{cards,quickActions,todoCount}`。
- [x] `GET /api/v1/nurse/workbench-summary` 返回 `{cards,quickActions,todoCount}`。
- [x] `GET /api/v1/admin/dashboard/overview` 返回 `{cards,quickActions,todoCount}`。
- [x] 四端首页均展示 3 张关键卡片。
- [x] 四端首页均展示权限过滤后的快捷入口。
- [x] 快捷入口可点击进入对应空列表/详情占位区。
- [x] Mock JSON 字段与 `docs/api/phase-04-api.md` 和数据字典一致。

## 证据记录

| 证据类型 | 路径或说明 | 验收人 | 结果 |
| --- | --- | --- | --- |
| 接口契约 | `docs/api/phase-04-api.md` | Codex | 通过 |
| Mock JSON | `mock/home/*.json` | Codex | 通过 |
| 前端验收 | `docs/stage-check/stage-04-四端首页占位看板-frontend.md` | Codex | 通过 |

