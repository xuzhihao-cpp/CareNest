# 阶段 3 验收记录：权限拦截 MVP（前端）

## 阶段目标

- 完成页面访问拦截、接口角色校验和按钮权限。
- 普通用户访问管理端接口返回 `403`。
- 前端按钮按 `permissionCode` 显示。

## 接口契约

阶段 3 前端只调用并模拟以下 PDF 指定接口：

- `GET /api/v1/auth/permissions`
- `POST /api/v1/admin/roles/{roleId}/permissions`

统一响应结构仍为：

```json
{"code":0,"message":"success","data":{},"traceId":"string"}
```

普通用户访问管理权限保存接口固定返回：

```json
{"code":403,"message":"无权限","data":{},"traceId":"mock-phase-03-role-permissions-forbidden"}
```

## 本次前端改动

- 新增阶段 3 类型与接口模拟：`frontend/src/types/stageThree.ts`、`frontend/src/api/stageThree.ts`。
- 新增权限 mock：`mock/phase-03/permissions-fixtures.json`、`frontend/src/mock/phase-03/permissions-fixtures.json`。
- 四端壳 `frontend/src/components/AppSurface.vue` 增加：
  - 登录态页面访问拦截。
  - 错角色访问 `403` 拦截。
  - `GET /api/v1/auth/permissions` 权限展示。
  - `POST /api/v1/admin/roles/{roleId}/permissions` 角色校验结果展示。
  - `permissionCode` 控制主操作按钮和管理端保存权限按钮。
- 端页面跳转改用 `uni.reLaunch`，避免 H5 tab 页面缓存旧登录态。
- `docs/api/phase-03-api.md` 与 `docs/dictionary/data-dictionary.md` 已同步阶段 3 字段和接口示例。

## 测试结果

| 项目 | 结果 |
| --- | --- |
| `pnpm typecheck` | 通过 |
| `pnpm build:h5` | 通过 |
| 未登录访问长辈端 | 显示 `401` |
| 长辈端读取权限 | `GET /api/v1/auth/permissions` 可见 |
| 长辈端访问管理权限保存接口 | 返回 `403` |
| 长辈账号直访管理端 | 显示 `403` 页面访问拦截 |
| 长辈账号管理按钮 | 不显示 |
| 管理员访问管理权限保存接口 | 返回 `0 success` |
| 管理员按钮权限 | 显示保存角色权限按钮 |
| Console error | 无 |

## 截图证据

- `docs/stage-check/stage-03-unauthorized.png`
- `docs/stage-check/stage-03-elder-permissions.png`
- `docs/stage-check/stage-03-elder-forbidden-admin.png`
- `docs/stage-check/stage-03-admin-permissions.png`

## 结论

阶段 3 前端 MVP 已完成。当前实现仍为 mock 模式，接口路径、统一响应结构、权限字段、403 错误码和角色枚举均已与 PDF/文档同步；后端真实权限接口完成后可按相同 DTO 替换 mock。

