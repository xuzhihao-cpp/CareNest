# Phase 02 验收记录：登录与角色菜单 MVP

## 阶段目标

实现用户侧后端认证权限 MVP：登录、退出、当前用户、角色菜单，固定演示账号进入不同首页。

## 验收清单

- [x] `POST /api/v1/auth/login` 支持固定演示账号登录。
- [x] `POST /api/v1/auth/logout` 注销当前 Bearer token。
- [x] `GET /api/v1/auth/me` 返回当前用户。
- [x] `GET /api/v1/auth/menus` 返回角色菜单。
- [x] `ELDER`、`FAMILY`、`NURSE`、`ADMIN` 四类账号登录成功且 `menus[0]` 不同。
- [x] `CUSTOMER_SERVICE` 固定演示账号已保留。
- [x] 错误密码返回固定错误码 `401`。
- [x] 新增字段同步到 `docs/dictionary/data-dictionary.md`。
- [x] Mock JSON 字段与真实接口 DTO 一致。

## 证据记录

| 证据类型 | 路径或说明 | 结果 |
| --- | --- | --- |
| 接口测试 | `.\.tools\apache-maven-3.9.9\bin\mvn.cmd -Dmaven.repo.local=.m2/repository test` | 通过，9 个认证接口测试 |
| 接口契约 | `docs/api/phase-02-auth-api.md` | 已补充 |
| Mock JSON | `mock/phase-02/auth-*.json` | 已补充 |
| 后端实现 | `backend-user/src/main/java/com/csu/carenest/user/auth/` | 已补充 |

## 备注

当前阶段使用内存演示账号仓库实现技术验证，不新增数据库字段名。`sys_user`、`sys_role`、`user_role`、`login_session` 的正式建表和初始化数据仍按成员1数据库交付接入。
