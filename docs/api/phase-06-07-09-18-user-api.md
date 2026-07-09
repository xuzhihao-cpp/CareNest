# 阶段 6/7/9/18 用户侧后端 API 契约

本文件按成员 2 开工文档定义整理，接口路径、字段名和返回结构不改名。实现以 `db/schema` 已存在表为准。

## 阶段 6 绑定授权

数据表：`elder_family_binding`、`authorization_scope`、`operation_log`。

### POST /api/v1/family/bindings

Request:

```json
{
  "elderInviteCode": "elder_001",
  "relationType": "DAUGHTER",
  "scopeCodes": ["HEALTH_VIEW"]
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bindingId": "binding_xxx",
    "elderId": "elder_001",
    "elderName": "张爷爷",
    "relationType": "DAUGHTER",
    "bindingStatus": "PENDING",
    "scopeCodes": ["HEALTH_VIEW"]
  },
  "traceId": "mock-6"
}
```

同阶段接口：

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/family/bindings` | 查询当前家属绑定列表 |
| POST | `/api/v1/elder/bindings/{bindingId}/approve` | 长辈确认绑定 |
| PUT | `/api/v1/family/bindings/{bindingId}/scopes` | 家属调整授权范围 |
| POST | `/api/v1/family/bindings/{bindingId}/revoke` | 家属撤销绑定 |

规则：绑定确认只能由绑定对应长辈本人完成；只有 `ACTIVE` 绑定且 `scopeCodes` 包含对应授权时，家属才可访问档案、地址、报告确认等资源。

## 阶段 7 长辈基础档案

数据表：`elder_profile`、`elder_contact`、`health_archive_change_log`。

### GET /api/v1/elders/{elderId}/profile

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "elderId": "elder_001",
    "profileVersion": "elder_001:2026-07-09T11:00:00"
  },
  "traceId": "mock-7"
}
```

### PUT /api/v1/elders/{elderId}/profile

Request:

```json
{
  "name": "张爷爷",
  "gender": "MALE",
  "birthDate": "1946-05-12",
  "careLevel": "LEVEL_3",
  "emergencyContacts": [
    {
      "contactName": "张小明",
      "contactPhone": "13800000002",
      "relationType": "SON"
    }
  ]
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "elderId": "elder_001",
    "profileVersion": "elder_001:2026-07-09T11:00:00"
  },
  "traceId": "mock-7"
}
```

同阶段接口：`GET /api/v1/family/elders`。

规则：家属修改档案必须拥有当前长辈的 `HEALTH_EDIT` 或 `ARCHIVE_EDIT` 授权。

## 阶段 9 服务地址

数据表：`service_address`。

### POST /api/v1/elders/{elderId}/service-addresses

Request:

```json
{
  "contactName": "张小明",
  "contactPhone": "13800000002",
  "regionCode": "310101",
  "detailAddress": "人民路200号2单元301",
  "isDefault": true
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "addressId": "address_xxx",
    "fullAddress": "310000310100310101人民路200号2单元301",
    "isDefault": true
  },
  "traceId": "mock-9"
}
```

同阶段接口：

| Method | Path |
| --- | --- |
| GET | `/api/v1/elders/{elderId}/service-addresses` |
| PUT | `/api/v1/service-addresses/{addressId}` |
| DELETE | `/api/v1/service-addresses/{addressId}` |

规则：同一长辈同一家属仅保留一个默认地址；地址必须属于已授权长辈。

## 阶段 16 报告确认与归档建议

文档要求数据表：`care_report_ack`、`health_info_review_task`。

当前 `db/schema` 尚未提供上述两张表，因此本阶段接口暂不实现，避免脱离数据库模型自建字段。待成员 1 提交 schema 后再按以下路径补齐：

| Method | Path |
| --- | --- |
| POST | `/api/v1/elder/reports/{reportId}/ack` |
| POST | `/api/v1/family/reports/{reportId}/ack` |
| POST | `/api/v1/family/reports/{reportId}/archive-suggestions/decision` |

Request:

```json
{
  "ackResult": "CONFIRMED",
  "satisfaction": 5,
  "remark": "服务已确认",
  "acceptedSuggestionIds": ["suggestion_001"]
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "ackId": "ack_mock_001",
    "ackResult": "CONFIRMED",
    "reportStatus": "CONFIRMED"
  },
  "traceId": "mock-16"
}
```

契约 mock：

| 场景 | 文件 |
| --- | --- |
| 长辈确认报告 | `mock/phase-16/elder-report-ack.json` |
| 家属确认报告 | `mock/phase-16/family-report-ack.json` |
| 家属处理归档建议 | `mock/phase-16/family-archive-suggestions-decision.json` |

## 阶段 18 联调验收与演示数据状态

### GET /api/v1/health

沿用阶段 1 契约字段，`dbConnected` 由真实数据源连接检测得到。

### GET /api/v1/admin/demo-data/status

管理员接口。统计现有 `sys_user`、`elder_profile`、`elder_family_binding`、`service_address` 演示数据是否就绪。

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "ready": true,
    "accounts": ["elder_demo", "family_demo", "nurse_demo", "admin_demo", "cs_demo"],
    "scenarioCount": 4
  },
  "traceId": "mock-18"
}
```
