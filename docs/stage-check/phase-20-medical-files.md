# 阶段 20 病历资料验收

## 自动检查

- `Phase20MedicalFileApiTest`：上传认证、真实内容签名、资产落库、家属登记、长辈读取、待绑定拒绝、护理端拒绝。
- `UserApiOpenApiContractTest`：冻结 `/files` 与 `/elders/{elderId}/medical-files`。
- 全量 Maven、前端类型与 OpenAPI 生成检查在最终验收时记录。

## 真实环境门禁

- [x] PDF 经 `http://localhost:3000/api/v1/files` 上传成功，MinIO 的 `smart-nursing/medical/2026-07-14/` 下存在对应对象。
- [x] `file_asset` 保存真实上传人、`application/pdf`、文件大小、bucket 和不透明 object key。
- [x] 登记后 `file_asset.audit_status` 与 `medical_file.audit_status` 均为 `PENDING`，两表通过同一 `file_id` 关联。
- [x] 刷新 GET 列表返回 3 条真实资料；最新资料为 `PENDING`，预览地址 authority 为 `http://localhost:19000`，响应不包含 object key。
- [x] 护理账号读取阶段 20 病历列表返回 HTTP `403`；待绑定家属由自动测试覆盖 `403`。
- [x] 以 Markdown 内容伪装 `application/pdf` 上传返回业务码 `422`，消息为“仅支持PDF、JPEG和PNG文件”。

## 最终自动结果

- `mvn -pl backend-user test`：56 tests，0 failures，0 errors。
- `pnpm typecheck`：通过。
- `pnpm contract:check`：通过。
