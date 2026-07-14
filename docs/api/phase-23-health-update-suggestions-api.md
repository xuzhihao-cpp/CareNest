# 阶段 23 健康档案变更建议接口

## POST /api/v1/orders/{orderId}/health-update-suggestions

仅订单当前分配的护理人员可提交。请求只接受 `fieldName`、结构化 `newValue`、`sourceType`、`sourceId` 和 5-255 字的 `reason`。

- `fieldName`: `diseases | medications | allergies | riskTags | carePlan`
- `sourceType`: `SERVICE_RECORD | SERVICE_REPORT`
- 来源必须真实存在并属于路径中的订单。
- 同订单、来源、字段和规范化新值已有 `PENDING` 建议时返回 HTTP 409，不新增任何记录。

成功返回：

```json
{"suggestionId":"...","status":"PENDING"}
```

建议、`health_info_review_task` 和 `operation_log` 在同一事务写入。该接口禁止更新 `health_archive` 及其子表。

## GET /api/v1/admin/health-review-tasks

要求 `ADMIN` 或 `CUSTOMER_SERVICE`，同时必须拥有启用的 `HEALTH_REVIEW`、`HEALTH_ARCHIVE_REVIEW` 或 `health:review` 权限之一。

支持 `page`、`size`（1-50）、`status`、`sourceType`、`keyword`。返回 `{records,total,page,size}`，记录包含长辈、服务、真实来源摘要、当前值、建议值、原因和状态。
