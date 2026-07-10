# 阶段 2 验收记录：登录与角色菜单 MVP（前端）

## 阶段目标

- 实现登录、退出、当前用户、角色菜单。
- 四类账号登录后由 `menus` 返回路径进入不同首页。
- 错误密码返回固定错误码。
- 护理端、长辈端、家属端继续保持 uni-app 移动端形态；管理端保持 H5 工作台形态。

## 接口契约

阶段 2 前端只调用并模拟以下 PDF 指定接口：

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/menus`

统一响应结构仍为：

```json
{"code":0,"message":"success","data":{},"traceId":"string"}
```

错误密码固定返回：

```json
{"code":401,"message":"用户名或密码错误","data":{},"traceId":"mock-phase-02-login-failed"}
```

## 演示账号

| 角色 | 用户名 | 密码 | 登录后首页 |
| --- | --- | --- | --- |
| ELDER | `elder_demo` | `CareNest@2026` | `/pages/elder/index` |
| FAMILY | `family_demo` | `CareNest@2026` | `/pages/family/index` |
| NURSE | `nurse_demo` | `CareNest@2026` | `/pages/nurse/index` |
| ADMIN | `admin_demo` | `CareNest@2026` | `/pages/admin/index` |

## 本次前端改动

- 新增登录页：`frontend/src/pages/login/index.vue`。
- 新增登录应用壳：`frontend/src/apps/login/LoginApp.vue`。
- 新增阶段 2 类型与接口模拟：`frontend/src/types/stageTwo.ts`、`frontend/src/api/stageTwo.ts`。
- 新增 auth mock：`mock/phase-02/auth-fixtures.json`、`frontend/src/mock/phase-02/auth-fixtures.json`。
- 四端壳 `frontend/src/components/AppSurface.vue` 增加当前用户、角色菜单、退出登录和未登录/无权限提示。
- `pages.json` 保留 uni-app tabBar 配置以兼容 H5 运行时，H5 中通过 CSS 隐藏原生 tabBar，避免遮挡移动端首屏操作区。
- `docs/api/phase-01-02-api.md` 与 `docs/dictionary/data-dictionary.md` 已同步登录字段和接口示例。

## 测试结果

| 项目 | 结果 |
| --- | --- |
| `pnpm typecheck` | 通过 |
| `pnpm build:h5` | 通过 |
| 错误密码 | 返回 `401 用户名或密码错误` |
| ELDER 登录 | 进入 `/pages/elder/index`，菜单可见 |
| FAMILY 登录 | 进入 `/pages/family/index`，菜单可见 |
| NURSE 登录 | 进入 `/pages/nurse/index`，菜单可见 |
| ADMIN 登录 | 进入 `/pages/admin/index`，菜单可见 |
| `GET /api/v1/health` | 四端可见 |
| `GET /api/v1/version` | 四端可见 |
| `UP` 状态 | 四端可见 |
| Console error | 无 |

## 截图证据

- `docs/stage-check/stage-02-login.png`
- `docs/stage-check/stage-02-login-wrong-password.png`
- `docs/stage-check/stage-02-elder.png`
- `docs/stage-check/stage-02-family.png`
- `docs/stage-check/stage-02-nurse.png`
- `docs/stage-check/stage-02-admin.png`

## 结论

阶段 2 前端 MVP 已完成。当前实现仍为 mock 模式，接口路径、统一响应结构、角色枚举和字段字典均已与 PDF/文档同步；后端真实接口完成后可按相同 DTO 替换 mock。
