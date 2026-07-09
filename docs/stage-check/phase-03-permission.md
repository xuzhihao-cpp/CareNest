# Phase 03 验收记录：权限拦截 MVP

## 阶段目标

实现权限查询、管理端角色权限配置和越权访问拦截。普通用户访问管理端接口必须返回 403，前端按钮显示以权限编码列表为准。

## 验收清单

- [x] `GET /api/v1/auth/permissions` 返回当前登录用户权限编码列表。
- [x] `POST /api/v1/admin/roles/{roleId}/permissions` 支持管理端更新角色权限。
- [x] 普通用户访问管理端权限配置接口返回 `403`。
- [x] 未登录用户访问权限查询接口返回 `401`。
- [x] 管理端接口二次校验 `ROLE_PERMISSION_MANAGE`。
- [x] 权限变更写入内存 `operation_log` 演示记录。
- [x] `sys_permission`、`role_permission`、`operation_log` 对应字段已同步到数据字典。
- [x] Mock JSON 字段与真实接口 DTO 一致。

## 证据记录

| 证据类型 | 路径或说明 | 结果 |
| --- | --- | --- |
| 接口测试 | `.\.tools\apache-maven-3.9.9\bin\mvn.cmd -Dmaven.repo.local=.m2/repository test` | 通过，根目录 Reactor Build Success，13 个认证权限接口测试 |
| 接口契约 | `docs/api/phase-03-permission-api.md` | 已补充 |
| Mock JSON | `mock/phase-03/*.json` | 已补充 |
| 后端实现 | `backend-user/src/main/java/com/csu/carenest/user/auth/` | 已补充 |

## 备注

当前阶段使用内存权限仓库完成核心链路和技术验证。正式表 `sys_permission`、`role_permission`、`operation_log` 后续按成员1数据库交付接入，接口字段保持本阶段契约不变。
