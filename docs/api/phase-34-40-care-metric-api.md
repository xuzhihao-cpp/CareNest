# 阶段34-40护理指标与留档 API

## 范围

本文冻结成员3在阶段34-40负责的护理端与管理端后端契约。阶段32-33的长辈提醒、家属提醒记录和管理端提醒统计主责不属于成员3，本模块未实现这些接口。

所有接口使用 `/api/v1`、`Authorization: Bearer <token>` 和全局统一响应。数据库字段沿用成员1阶段32-55契约，不在成员3分支新增表或同义状态。

## 阶段34：护理指标配置

- `GET /api/v1/admin/service-items/{serviceId}/care-metric-config`
- `PUT /api/v1/admin/service-items/{serviceId}/care-metric-config`
- 权限：`ADMIN/CUSTOMER_SERVICE + CARE_METRIC_CONFIG_MANAGE`

PUT 请求：

```json
{
  "items": [
    {
      "metricCode": "SERVICE_PHOTO",
      "metricName": "服务照片",
      "metricType": "SERVICE_PROCESS",
      "required": true,
      "evidenceType": "PHOTO",
      "scoreWeight": 10,
      "description": "服务中必须拍照"
    }
  ]
}
```

GET/PUT 的 `data` 均为 `{"configVersion":1}`。更新配置会创建新版本并停用旧版本，不覆盖历史指标项。

## 阶段35：订单留档清单

- `POST /api/v1/admin/orders/{orderId}/metric-checklist/generate`
- `GET /api/v1/nurse/orders/{orderId}/metric-checklist`

响应：

```json
{
  "items": [
    {
      "itemId": "order_metric_xxx",
      "metricCode": "SERVICE_PHOTO",
      "required": true,
      "evidenceType": "PHOTO",
      "expectedAction": null,
      "status": "PENDING",
      "scoreWeight": 10
    }
  ]
}
```

生成接口要求 `CARE_METRIC_CONFIG_MANAGE`。清单每单唯一，重复生成返回原清单；指标编码、名称、证据类型和权重保存订单快照，后续配置更新不改写历史订单。

## 阶段36-37：护理留档与审核

- `POST /api/v1/nurse/orders/{orderId}/evidences`
- `GET /api/v1/orders/{orderId}/evidences`
- `GET /api/v1/admin/evidences`
- `POST /api/v1/admin/evidences/{evidenceId}/review`

留档请求固定为：

```json
{
  "metricItemId": "order_metric_xxx",
  "fileId": "file_xxx",
  "evidenceType": "PHOTO",
  "description": "服务照片"
}
```

留档和审核响应记录固定为 `{"evidenceId":"evidence_xxx","auditStatus":"PENDING"}`。两个 GET 列表的 `data` 是该固定记录的数组，不增加分页或同义字段。

审核请求固定为 `{"auditStatus":"APPROVED","reviewComment":"材料有效"}`。审核目标只能是 `APPROVED/REJECTED/NEED_MORE`，驳回和补充材料必须填写意见。审核要求 `CARE_EVIDENCE_REVIEW`，结果与 `evidence_review_record` 在同一事务写入。

## 阶段38：指标完成校验

- `POST /api/v1/orders/{orderId}/metric-check`
- `GET /api/v1/orders/{orderId}/metric-check-result`

响应：

```json
{
  "items": [
    {
      "metricItemId": "order_metric_xxx",
      "metricName": "服务照片",
      "checkResult": "MISSING",
      "scoreImpact": -10,
      "missingEvidence": true
    }
  ]
}
```

服务结束状态才允许执行校验。必填且要求证据的指标必须存在已审核通过的留档，否则进入 `MISSING`；已有待审证明或最终豁免结论不会被重复校验覆盖。

## 阶段39：未完成原因与证明

- `POST /api/v1/nurse/metric-items/{metricItemId}/exception-proofs`
- `GET /api/v1/nurse/orders/{orderId}/exception-proofs`

请求固定为：

```json
{
  "reasonType": "ELDER_REFUSED",
  "reasonText": "长辈当次拒绝拍照",
  "fileIds": ["file_xxx"]
}
```

`reasonType` 只能是 `FORGOT/NOT_REQUIRED/ELDER_REFUSED/OBJECTIVE_IMPOSSIBLE/OTHER`。响应记录固定为 `{"proofId":"proof_xxx","reviewStatus":"PENDING"}`；GET 的 `data` 是固定记录数组。提交后指标进入 `PENDING_PROOF`，同一指标不能同时存在两个待审核证明。

## 阶段40：管理端豁免审核

- `GET /api/v1/admin/metric-exception-proofs`
- `POST /api/v1/admin/metric-exception-proofs/{proofId}/review`
- 权限：`ADMIN/CUSTOMER_SERVICE + CARE_EVIDENCE_REVIEW`

请求固定为：

```json
{
  "reviewResult": "APPROVED",
  "reviewComment": "证明有效",
  "scoreDecision": "NO_DEDUCTION"
}
```

`reviewResult=APPROVED` 必须对应 `scoreDecision=NO_DEDUCTION`，指标进入 `EXEMPT_APPROVED` 且评分影响为 0；`reviewResult=REJECTED` 必须对应 `scoreDecision=DEDUCT`，指标进入 `EXEMPT_REJECTED` 并按订单快照权重记录负分。

`scoreDecision` 是响应所需的审核决策值，不写入数据库状态列，也不新增数据字典状态。证明结论、指标状态、`nurse_metric_record` 和 `operation_log` 在同一事务提交。

## 状态与事实源

- 指标状态：`PENDING/SUBMITTED/PASS/MISSING/PENDING_PROOF/EXEMPT_APPROVED/EXEMPT_REJECTED`
- 留档审核：`PENDING/APPROVED/REJECTED/NEED_MORE`
- 证明审核：`PENDING/APPROVED/REJECTED`
- MySQL 是事实源；本模块不使用运行时 mock 代替写库。
- `file_asset` 只校验文件元数据和上传人，文件字节、MinIO 桶名和对象路径不复制到留档表或接口响应。
