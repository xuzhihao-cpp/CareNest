# 阶段 24 健康档案变更记录接口

统一前缀为 `/api/v1`，请求使用 Bearer Token，响应沿用 `{code,message,data,traceId}`。

## 用户侧读取

`GET /elders/{elderId}/health-archive/change-logs`

- 长辈仅可读取 `elder_profile.user_id` 对应的本人记录。
- 家属必须与目标长辈存在 `ACTIVE` 绑定，且生效授权包含 `HEALTH_VIEW`。
- 返回最近 20 条真实 `health_archive_change_log` 记录，按变更时间倒序排列。
- 无权访问返回 `403`，目标长辈不存在返回 `404`。
- 响应不返回审核人员账号等内部身份信息。

## 管理侧读取

`GET /admin/elders/{elderId}/health-archive/change-logs`

该接口仅供管理端健康信息审核工作台使用，继续执行管理端角色和权限码校验。用户端不得复用此路径。

## 响应数据

每条记录包含 `changeLogId`、`fieldName`、`changeType`、`beforeValue`、`afterValue`、`comment`、`archiveVersion` 和 `changedAt`。前端只展示业务名称、调整前后内容、说明和时间，不显示技术 ID 或档案版本。

本接口只读取 MySQL 真实数据，不使用 Redis 缓存、mock 数据或失败回退。
