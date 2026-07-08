# 阶段 3 API 契约：权限拦截 MVP

本文件冻结阶段 3 权限接口契约。前端 mock、后端 DTO、接口测试均以本文件和 `docs/dictionary/data-dictionary.md` 为准。

## GET /api/v1/auth/permissions

用途：获取当前登录用户角色对应的按钮权限。

权限：已登录用户，按角色与授权校验。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roleCode": "ADMIN",
    "permissions": ["admin:dashboard:view", "role:permission:update"]
  },
  "traceId": "mock-phase-03-permissions"
}
```

### Error Example

```json
{
  "code": 401,
  "message": "未登录",
  "data": {},
  "traceId": "mock-phase-03-permissions-unauthorized"
}
```

## POST /api/v1/admin/roles/{roleId}/permissions

用途：管理端保存某个角色的权限编码集合。

权限：仅管理员且具备 `role:permission:update` 权限可访问；普通用户访问必须返回 `403`。

### Request

```json
{
  "permissionCodes": ["role:permission:update"]
}
```

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roleCode": "ADMIN",
    "permissions": ["role:permission:update"]
  },
  "traceId": "mock-phase-03-role-permissions"
}
```

### Error Example

```json
{
  "code": 403,
  "message": "无权限",
  "data": {},
  "traceId": "mock-phase-03-role-permissions-forbidden"
}
```

