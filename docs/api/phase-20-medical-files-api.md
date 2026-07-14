# 阶段 20 病历资料接口

## 文件规则

- 上传接口：`POST /api/v1/files`，字段名固定为 `file`。
- 支持 PDF、JPEG、PNG，后端同时校验声明 MIME 与文件签名。
- 空文件、超过 20 MiB、扩展名或 MIME 伪装返回 `422`。
- 原文件名仅作展示并清理路径与控制字符；对象 key 由后端生成且永不返回。
- 预览 URL 有效期 10 分钟，只有通过资源权限校验后才生成。

## POST /api/v1/files

任意已登录角色可以上传自己的文件资产。成功 `data`：

```json
{"fileId":"...","url":"短期授权地址","originalName":"report.pdf","mimeType":"application/pdf","size":1024,"auditStatus":"PENDING"}
```

上传对象成功但数据库写入失败时，后端尝试删除对应 MinIO 对象，避免无主文件。

## POST /api/v1/elders/{elderId}/medical-files

仅 `FAMILY + ACTIVE + HEALTH_EDIT`。请求：

```json
{"fileId":"...","fileType":"EXAMINATION_REPORT","title":"血压检查报告","occurredAt":"2026-07-01"}
```

`fileId` 必须属于当前用户且尚未登记。成功状态固定为 `PENDING`；前端将其显示为“待审核”。

## GET /api/v1/elders/{elderId}/medical-files

长辈本人或 `FAMILY + ACTIVE + HEALTH_VIEW` 可读取。护理端在阶段 20 不可读取；阶段 25 通过独立摘要接口仅读取审核通过资料。

状态兼容：数据库/API 使用 `PENDING | APPROVED | REJECTED | NEED_MORE`，现有前端将 `PENDING` 归一化为 `PENDING_REVIEW`，将 `NEED_MORE` 显示为“需补充”。
