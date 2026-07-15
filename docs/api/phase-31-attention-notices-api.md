# 阶段31服务前注意事项 API

## 范围

本文冻结成员3在阶段31负责的护理端与管理端后端契约。数据库字段沿用
`docs/dictionary/phase-26-31-nurse-admission-data-dictionary.md`，不新增同义状态或替代接口。

## GET /api/v1/nurse/orders/{orderId}/attention-notices

读取当前订单的服务前注意事项。首次读取会根据已归档健康档案、服务项目和派单上下文
生成 MySQL 快照；病历审核结果只有归档为结构化健康数据后才参与生成，文件标题不能替代
审核摘要。相同来源和规范内容重复读取不会新增重复记录。

允许访问：

- 被派护理本人。
- 同时具备 `CARE_ATTENTION_REVIEW` 的 `ADMIN` 或 `CUSTOMER_SERVICE`，只读审阅。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "noticeId": "notice_xxx",
        "level": "CRITICAL",
        "content": "已归档过敏信息：青霉素。护理过程中不得自行建议用药调整。",
        "source": "HEALTH_ARCHIVE",
        "requiredAck": true,
        "acknowledged": false,
        "acknowledgedAt": null
      }
    ]
  },
  "traceId": "string"
}
```

`level` 只允许 `INFO/WARNING/CRITICAL`；`source` 只允许
`HEALTH_ARCHIVE/MEDICAL_FILE/SERVICE_ITEM/ORDER_CONTEXT`。

## POST /api/v1/nurse/orders/{orderId}/attention-notices/ack

被派护理批量确认当前订单可见的注意事项。

权限：`NURSE + NURSE_ATTENTION_ACK`，并校验当前任务归属。订单仅在
`DISPATCHED/ACCEPTED/ON_THE_WAY` 时允许确认，取消或已开始服务后返回状态冲突。

请求：

```json
{
  "noticeIds": ["notice_xxx", "notice_yyy"]
}
```

`noticeIds` 必须为 1 至 100 个非空字符串，且全部属于当前订单、当前任务和当前护理。
重复提交保持首次确认时间并返回最新 GET 读模型。

## 开始服务门禁

现有 `POST /api/v1/nurse/tasks/{taskId}/status` 在 `targetStatus=SERVING` 时会重新生成
当前来源快照，并检查所有 `requiredAck=true` 的有效记录。存在未确认项时返回 `422`，
任务和订单状态均保持不变；该规则在后端事务内执行，不能通过直接调用接口绕过。

## 错误规则

| code | 场景 |
| --- | --- |
| `400` | `noticeIds` 为空、超过上限或含空值 |
| `401` | Bearer Token 缺失或失效 |
| `403` | 非关联护理、缺少冻结权限码或非管理审阅角色 |
| `404` | 订单或关联护理任务不存在 |
| `409` | 订单已取消、状态不允许确认或任务归属已变化 |
| `422` | noticeId 不属于当前可见集合，或开始服务前仍有必确认项 |

## 数据与隐私规则

- MySQL 的 `care_attention_notice` 和 `care_attention_ack` 是事实源，不使用运行时 mock。
- 已确认来源发生变化时不覆盖历史快照；旧记录标记为 `CANCELED`，新内容创建新快照。
- 风险等级或必确认语义变化会创建新快照；来源删除后旧记录标记为 `CANCELED`。
- 重新派单后确认记录按 `nurse_id` 隔离，新护理必须重新确认。
- 响应不返回健康档案版本、病历 ID、对象存储路径或完整病历正文。
