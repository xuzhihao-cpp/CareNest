# CareNest 全局接口契约

## 基础规则

| 规范项 | 统一要求 |
| --- | --- |
| API 前缀 | `/api/v1` |
| 认证方式 | `Authorization: Bearer <token>` |
| 成功返回 | `{code:0,message:"success",data:{},traceId:"string"}` |
| 分页返回 | `data={records:[],total:0,page:1,size:10}` |
| 时间格式 | `2026-07-07T19:00:00+08:00` |
| 文件上传 | `multipart/form-data` |
| 上传返回 | `{fileId,url,type,auditStatus}` |
| ID 命名 | `elderId`、`familyId`、`nurseId`、`orderId`、`serviceId` |
| 状态来源 | 状态值统一维护在 `docs/dictionary/data-dictionary.md` |

## 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "string"
}
```

## 统一分页结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 10
  },
  "traceId": "string"
}
```

## 通用错误码

| code | message 建议 | 场景 |
| --- | --- | --- |
| 400 | 参数错误 | 请求参数格式错误或缺少必填字段 |
| 401 | 未登录 | 未携带有效 token |
| 403 | 无权限 | 角色或按钮权限不足 |
| 404 | 不存在 | 资源不存在 |
| 409 | 状态冲突 | 当前状态不允许目标操作 |
| 422 | 业务规则失败 | 业务校验不通过 |
| 500 | 服务异常 | 未预期服务端错误 |

## 命名规则

- API 字段：`camelCase`。
- 数据库列：`snake_case`。
- 枚举值：大写英文。
- 后端 DTO 与前端 TypeScript interface 使用同名字段。
- 数据库列与 API 字段由后端映射，前端不得直接使用 `snake_case` 字段。

## 变更规则

任何接口路径、请求字段、响应字段、状态枚举的变更，必须同步更新：

- `docs/dictionary/data-dictionary.md`
- `docs/api/`
- `mock/`
- 对应 `docs/stage-check/phase-XX.md`