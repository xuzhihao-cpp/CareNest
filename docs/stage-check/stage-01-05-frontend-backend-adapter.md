# 阶段 1-5 前端连接后端适配验收记录

日期：2026-07-09

## 范围

- 当前后端已实现阶段 2-3 用户侧认证与权限接口。
- 本次只调整前端请求层、类型适配、权限映射、dev proxy 与前端 mock 数据。
- 阶段 1 `health/version` 与阶段 4 首页 summary 后端尚未实现，按现有文档继续使用同结构 mock 兜底，但接口路径保持 `/api/v1` 契约不变。

## 关键适配

- `menus: string[]` 后端 DTO 已在前端归一化为 `AuthMenu[]`，登录后仍进入 uni-app H5 页面：
  - `/elder/home` -> `/pages/elder/index`
  - `/family/home` -> `/pages/family/index`
  - `/nurse/home` -> `/pages/nurse/index`
  - `/admin/home` -> `/pages/admin/index`
- 后端大写权限码在前端展开为页面按钮别名，同时保留原始权限码展示。
- `POST /api/v1/admin/roles/{roleId}/permissions` 前端提交时会把按钮别名转换回后端权限码。
- Vite dev server 已配置 `/api/v1` -> `http://127.0.0.1:8081`，用于浏览器 real 模式联调。

## 命令验证

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端类型检查 | `pnpm typecheck` | 通过 |
| 前端 H5 构建 | `pnpm build:h5` | 通过 |
| Java 环境 | `java -version` | Java 21.0.5 |
| Maven 环境 | `mvn -version` | Maven 3.9.9 / Java 21 |
| 后端用户模块测试 | `mvn -pl backend-user test` | Tests run: 13, Failures: 0, Errors: 0 |

## 真实接口验证

直连后端 `http://127.0.0.1:8081/api/v1`：

- `POST /auth/login`：`code=0`，`roles=ADMIN`，`menus[0]=/admin/home`
- `GET /auth/me`：`code=0`
- `GET /auth/menus`：`code=0`
- `GET /auth/permissions`：`ADMIN_DASHBOARD_VIEW, ROLE_PERMISSION_MANAGE`
- `POST /admin/roles/NURSE/permissions`：`code=0`，返回 `NURSE_ORDER_VIEW, NURSE_REPORT_CREATE, NURSE_APPEAL_CREATE`

经前端代理 `http://127.0.0.1:5173/api/v1`：

- `POST /auth/login`：`code=0`，`menus[0]=/family/home`
- `GET /auth/menus`：`code=0`
- `GET /auth/permissions`：`FAMILY_ELDER_VIEW, FAMILY_ORDER_CREATE`
- 家属账号访问管理端权限接口：HTTP 403，`code=403`，`message=无权限`

## 浏览器验收

浏览器运行环境：

- 前端：`VITE_USE_MOCK=false`、`VITE_FRONTEND_API_BASE=/api/v1`、`pnpm dev`
- 后端：`mvn -pl backend-user spring-boot:run`

四端均通过 UI 登录进入对应页面，页面断言均无缺失项：

| 端 | 路由 | 核心断言 | 截图 |
| --- | --- | --- | --- |
| 长辈端 | `#/pages/elder/index` | `mode real`、`UP`、`GET /api/v1/health`、`ELDER_REMINDER_VIEW`、`一键求助` | `docs/stage-check/real-mode-elder.png` |
| 家属端 | `#/pages/family/index` | `mode real`、`UP`、`GET /api/v1/version`、`FAMILY_ELDER_VIEW`、`去确认` | `docs/stage-check/real-mode-family.png` |
| 护理端 | `#/pages/nurse/index` | `mode real`、`UP`、`NURSE_ORDER_VIEW`、`提交报告` | `docs/stage-check/real-mode-nurse.png` |
| 管理端 | `#/pages/admin/index` | `mode real`、`UP`、`ADMIN_DASHBOARD_VIEW`、`处理队列` | `docs/stage-check/real-mode-admin.png` |

应用 console error：0。

## 结论

阶段 1-5 前端骨架在 real 模式下已能连接当前后端阶段 2-3 接口；后端尚未实现的阶段 1/4 接口继续按文档使用同结构 mock 兜底，不影响后续阶段在同一前端骨架上继续扩展。
