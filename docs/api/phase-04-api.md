# 阶段 4 API 契约：四端首页占位看板

本文件冻结阶段 4 首页 summary 接口契约。后端未完成前，前端只允许使用同结构 mock。

## 统一请求 DTO

```json
{
  "role": "ELDER",
  "currentUserId": "user-elder-001"
}
```

## 统一响应 DTO

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "cards": [
      {"key":"todayReminders","label":"今日提醒","value":"4","unit":"项","trend":"2 项待完成"}
    ],
    "quickActions": [
      {"key":"todayReminders","label":"提醒列表","path":"/pages/elder/index?view=today-reminders","permissionCode":"reminder:update"}
    ],
    "todoCount": 2
  },
  "traceId": "mock-4"
}
```

## GET /api/v1/elder/home-summary

用途：长辈端首页占位看板。

权限：`ELDER`，或授权 `FAMILY`。

## GET /api/v1/family/home-summary

用途：家属端首页占位看板。

权限：`FAMILY`，校验 `ACTIVE` 绑定和 scope。

## GET /api/v1/nurse/workbench-summary

用途：护理端工作台占位看板。

权限：`NURSE`；管理场景允许 `ADMIN`。

## GET /api/v1/admin/dashboard/overview

用途：管理端运营概览占位看板。

权限：`ADMIN` 或 `CUSTOMER_SERVICE`，按 `permissionCode` 校验。

