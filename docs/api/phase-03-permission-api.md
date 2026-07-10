# 阶段 3 API 契约：权限拦截 MVP

本文档对应 PDF v2 的阶段 3“权限拦截 MVP”。接口统一前缀为 `/api/v1`，统一返回 `{code,message,data,traceId}`。

## 固定权限点

| roleCode | 默认 permissions |
| --- | --- |
| `ELDER` | `ELDER_REMINDER_VIEW`, `ELDER_AI_CHAT` |
| `FAMILY` | `FAMILY_ELDER_VIEW`, `FAMILY_ORDER_CREATE` |
| `NURSE` | `NURSE_ORDER_VIEW`, `NURSE_REPORT_CREATE` |
| `ADMIN` | `ADMIN_DASHBOARD_VIEW`, `ROLE_PERMISSION_MANAGE` |
| `CUSTOMER_SERVICE` | `CUSTOMER_SERVICE_TICKET_HANDLE`, `ROLE_PERMISSION_MANAGE` |

## GET /api/v1/auth/permissions

用途：登录态接口，返回当前用户可用按钮权限。前端按钮显示必须读取 `permissions`，不得按角色硬编码按钮。

认证：`Authorization: Bearer <token>`

### Response 200

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roleCode": "FAMILY",
    "permissions": ["FAMILY_ELDER_VIEW", "FAMILY_ORDER_CREATE"]
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

## POST /api/v1/admin/roles/{roleId}/permissions

用途：管理端角色权限配置接口。所有管理端接口必须二次校验权限，普通用户访问必须返回 403。

认证：`Authorization: Bearer <token>`

权限要求：当前用户角色为 `ADMIN` 或 `CUSTOMER_SERVICE`，且拥有 `ROLE_PERMISSION_MANAGE`。

路径参数：`roleId`。阶段 3 MVP 暂用 `roleCode` 作为路径值，可选值为 `ELDER`、`FAMILY`、`NURSE`、`ADMIN`、`CUSTOMER_SERVICE`。

### Request

```json
{
  "permissionCodes": ["NURSE_ORDER_VIEW", "NURSE_REPORT_CREATE", "NURSE_APPEAL_CREATE"]
}
```

### Response 200

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roleCode": "NURSE",
    "permissions": ["NURSE_ORDER_VIEW", "NURSE_REPORT_CREATE", "NURSE_APPEAL_CREATE"]
  },
  "traceId": "trace-demo"
}
```

### Response 403

```json
{
  "code": 403,
  "message": "无权限",
  "data": null,
  "traceId": "trace-demo"
}
```

## 字段锁定

本阶段新增或使用的字段必须已记录在 `docs/dictionary/data-dictionary.md`：

- `roleCode`
- `roleId`
- `permissions`
- `permissionCodes`
- `permissionCode`
- `operationType`
