# 阶段 2 API 契约：登录与角色菜单 MVP

本文档对应 PDF v2 的阶段 2“登录与角色菜单 MVP”。接口统一前缀为 `/api/v1`，统一返回 `{code,message,data,traceId}`。

## 固定演示账号

| roleCode | username | password | 首页菜单 |
| --- | --- | --- | --- |
| `ELDER` | `elder_demo` | `Demo@123456` | `/elder/home` |
| `FAMILY` | `family_demo` | `Demo@123456` | `/family/home` |
| `NURSE` | `nurse_demo` | `Demo@123456` | `/nurse/home` |
| `ADMIN` | `admin_demo` | `Demo@123456` | `/admin/home` |
| `CUSTOMER_SERVICE` | `cs_demo` | `Demo@123456` | `/customer-service/home` |

## POST /api/v1/auth/login

用途：公开登录接口，返回 token、当前用户和角色菜单。前端必须以 `menus[0]` 作为角色首页入口，不得硬编码角色首页。

### Request

```json
{
  "username": "elder_demo",
  "password": "Demo@123456"
}
```

### Response 200

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "cn-demo-token",
    "userId": "elder-001",
    "displayName": "长辈演示账号",
    "roles": ["ELDER"],
    "menus": ["/elder/home", "/elder/reminders", "/elder/ai"]
  },
  "traceId": "trace-demo"
}
```

### Response 401

```json
{
  "code": 401,
  "message": "未登录",
  "data": null,
  "traceId": "trace-demo"
}
```

## POST /api/v1/auth/logout

用途：登录态接口，注销当前 Bearer token。

认证：`Authorization: Bearer <token>`

响应字段同 `AuthResponse`，注销后原 token 再访问 `/api/v1/auth/me` 必须返回 401。

## GET /api/v1/auth/me

用途：登录态接口，返回当前用户。

认证：`Authorization: Bearer <token>`

响应字段同 `AuthResponse`。

## GET /api/v1/auth/menus

用途：登录态接口，返回当前用户角色菜单。

认证：`Authorization: Bearer <token>`

响应字段同 `AuthResponse`，其中 `menus` 为菜单路径列表。

## 字段锁定

本阶段新增或使用的字段必须已记录在 `docs/dictionary/data-dictionary.md`：

- `username`
- `password`
- `token`
- `userId`
- `displayName`
- `roles`
- `menus`
