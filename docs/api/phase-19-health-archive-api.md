# 阶段 19 健康档案 API

统一前缀为 `/api/v1`，请求使用 Bearer Token，响应沿用 `{ code, message, data, traceId }`。

## 权限

- 长辈只能读取 `elder_profile.user_id` 对应的本人档案。
- 家属读取要求与目标长辈存在 `ACTIVE` 绑定，且生效 `scope_codes` 包含 `HEALTH_VIEW`。
- 家属保存完整档案或快捷新增用药要求 `ACTIVE` 绑定和 `HEALTH_EDIT`。
- 长辈、护理人员、管理员和无关家属不能调用写入接口。

## GET `/elders/{elderId}/health-archive`

返回 `archiveVersion`、慢病、当前用药、过敏记录、风险标签、照护计划和更新时间。风险标签同时返回稳定的 `tagCode` 与用户可读的 `tagName`。档案不存在返回 404，资源无权读取返回 403。

## PUT `/elders/{elderId}/health-archive`

请求字段与前端 `HealthArchiveUpdateRequest` 一致：

```json
{
  "archiveVersion": 1,
  "diseases": [],
  "medications": [],
  "allergies": [],
  "riskTags": ["FALL_RISK"],
  "carePlan": {
    "careGoals": "保持状态稳定",
    "dailyCare": "每日记录健康情况",
    "precautions": "起身和移动时注意防跌倒"
  }
}
```

成功时主表版本加一，五类子数据在同一事务内整体替换，并写入 `health_archive_change_log` 和 `operation_log`。版本不匹配返回 409，重复项目或日期顺序错误返回 422，格式与枚举错误返回 400。失败不会留下半套子数据或审计日志。

## POST `/elders/{elderId}/medications`

请求携带当前 `archiveVersion` 及一个完整用药项目。药物名称按去除首尾空格、忽略大小写判重；成功后档案版本加一并返回新版本和新增用药。该接口只记录家庭提供的用药信息，不进行诊断、剂量推荐或自动调整。

## Redis 一致性

健康档案正文不缓存。写入提交后，系统失效目标长辈首页以及全部有效绑定家属首页缓存；事务回滚不失效缓存，Redis 不可用不改变 MySQL 写入与版本冲突结果。
