# 阶段 32-33 提醒中心 API

所有接口由 `backend-user:8081` 提供，必须携带当前长辈本人 Bearer Token。提醒内容来自真实 MySQL `reminder_task`，执行动作同时写入 `reminder_record` 和 `operation_log`。

## 查询提醒任务

`GET /api/v1/elder/reminders?page=1&size=20&status=PENDING`

`status` 可选值：`PENDING`、`DONE`、`SNOOZED`、`MISSED`、`NEED_HELP`。返回分页任务，前端不得展示内部来源标识。

## 执行提醒

`POST /api/v1/elder/reminders/{reminderId}/actions`

```json
{"action":"DONE"}
```

`action` 可选值：`DONE`、`SNOOZE`、`NEED_HELP`。`SNOOZE` 必须携带 `snoozeMinutes`，范围为 5 分钟至 24 小时。状态转换使用乐观条件更新，重复或过期操作返回 `409`。

## 查询执行记录

`GET /api/v1/elder/reminders/records?page=1&size=20`

阶段 33 返回按时间倒序排列的执行记录，包含提醒标题、动作、前后状态、备注和执行时间，不返回数据库内部记录 ID。

## 权限与验收

- 长辈只能读取和操作自己的提醒；家属、护理、管理账号访问用户提醒接口返回 `403`。
- 任务状态和记录状态只使用 `PENDING/DONE/SNOOZED/MISSED/NEED_HELP`。
- 重复请求不会产生无效状态转换；每次成功动作产生一条执行记录和一条操作日志。
