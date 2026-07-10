# 阶段 1-2 API 契约

本文件只冻结接口契约，不代表阶段 1-2 已实现后端代码。

## GET /api/v1/health

用途：开发内部健康检查。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "appName": "CareNest",
    "version": "0.1.0",
    "dbConnected": false,
    "serverTime": "2026-07-08T09:00:00+08:00"
  },
  "traceId": "mock-phase-01-health"
}
```

## GET /api/v1/version

用途：查看构建与接口前缀信息。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "gitCommit": "local-kickoff",
    "buildTime": "2026-07-08T09:00:00+08:00",
    "apiPrefix": "/api/v1"
  },
  "traceId": "mock-phase-01-version"
}
```

## GET /api/v1/dictionaries

用途：查看数据字典目录。阶段 2 使用 `dictCode=ALL` 返回当前核心字典清单。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dictCode": "ALL",
    "dictName": "全部核心字典",
    "items": [
      {"value":"roleCode","label":"角色枚举","sort":1,"enabled":true,"remark":"角色和权限使用"},
      {"value":"orderStatus","label":"订单状态","sort":2,"enabled":true,"remark":"预约与护理履约使用"}
    ]
  },
  "traceId": "mock-phase-02-dictionaries"
}
```

## GET /api/v1/dictionaries/{dictCode}

用途：按 `dictCode` 查看某个字典的值。

### Response Example: roleCode

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dictCode": "roleCode",
    "dictName": "角色枚举",
    "items": [
      {"value":"ELDER","label":"长辈","sort":1,"enabled":true,"remark":"长辈端用户"},
      {"value":"FAMILY","label":"家属","sort":2,"enabled":true,"remark":"家属端用户"},
      {"value":"NURSE","label":"护理人员","sort":3,"enabled":true,"remark":"护理端用户"},
      {"value":"ADMIN","label":"管理员","sort":4,"enabled":true,"remark":"管理端管理员"},
      {"value":"CUSTOMER_SERVICE","label":"客服","sort":5,"enabled":true,"remark":"客服与工单处理"}
    ]
  },
  "traceId": "mock-phase-02-role-code"
}
```

## POST /api/v1/auth/login

用途：登录并返回 token、当前用户和角色菜单。阶段 2 使用固定演示账号。

### Request

```json
{
  "username": "elder_demo",
  "password": "CareNest@2026"
}
```

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "mock-token-elder",
    "userId": "user-elder-001",
    "displayName": "张奶奶",
    "roles": ["ELDER"],
    "menus": [
      {"name":"长辈首页","path":"/pages/elder/index","icon":"home"}
    ]
  },
  "traceId": "mock-phase-02-login"
}
```

### Error Example

```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": {},
  "traceId": "mock-phase-02-login-failed"
}
```

## POST /api/v1/auth/logout

用途：退出登录，清理当前 token。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "mock-phase-02-logout"
}
```

## GET /api/v1/auth/me

用途：获取当前登录用户。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "user-elder-001",
    "displayName": "张奶奶",
    "roles": ["ELDER"],
    "menus": [
      {"name":"长辈首页","path":"/pages/elder/index","icon":"home"}
    ]
  },
  "traceId": "mock-phase-02-me"
}
```

## GET /api/v1/auth/menus

用途：获取当前登录用户的角色菜单。

### Response

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "menus": [
      {"name":"长辈首页","path":"/pages/elder/index","icon":"home"},
      {"name":"今日提醒","path":"/pages/elder/index","icon":"reminder"}
    ]
  },
  "traceId": "mock-phase-02-menus"
}
```

## 字段锁定

阶段 1-2 中出现的字段必须已记录在 `docs/dictionary/data-dictionary.md`：

- `code`
- `message`
- `data`
- `traceId`
- `status`
- `appName`
- `version`
- `dbConnected`
- `serverTime`
- `gitCommit`
- `buildTime`
- `apiPrefix`
- `dictCode`
- `dictName`
- `items`
- `value`
- `label`
- `sort`
- `enabled`
- `remark`
- `username`
- `password`
- `token`
- `userId`
- `displayName`
- `roles`
- `menus`
- `name`
- `path`
- `icon`
