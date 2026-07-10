# Phase 03 验收记录：权限拦截 MVP

## 阶段目标

完成页面访问拦截、接口角色校验和按钮权限。

## 验收清单

- [x] `GET /api/v1/auth/permissions` 返回当前角色权限集合。
- [x] `POST /api/v1/admin/roles/{roleId}/permissions` 普通用户访问返回 `403`。
- [x] 未登录访问端页面显示 `401` 拦截状态。
- [x] 错角色访问页面显示 `403` 拦截状态。
- [x] 前端按钮按 `permissionCode` 显示，普通用户不显示管理端保存权限按钮。
- [x] 管理员具备 `role:permission:update` 时显示保存角色权限按钮。
- [x] Mock JSON 字段与 `docs/api/phase-03-api.md` 和数据字典一致。

## 证据记录

| 证据类型 | 路径或说明 | 验收人 | 结果 |
| --- | --- | --- | --- |
| 接口契约 | `docs/api/phase-03-api.md` | Codex | 通过 |
| Mock JSON | `mock/phase-03/permissions-fixtures.json` | Codex | 通过 |
| 前端验收 | `docs/stage-check/stage-03-权限拦截MVP-frontend.md` | Codex | 通过 |

