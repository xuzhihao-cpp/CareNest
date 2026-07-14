# 阶段 22 验收记录

## 自动化

- `Phase22HealthFeedbackApiTest`: 长辈身份推导、角色隔离、ACTIVE + HEALTH_VIEW、严重程度排序、语音归属、语音日志、高严重度审计、医疗文件边界。
- `Phase20MedicalFileApiTest`: 文件上传与病历登记回归。
- `UserApiOpenApiContractTest`: 冻结阶段 22 路由和响应结构。

## Docker 真实链路

- [x] 长辈上传真实 MP3 并提交反馈，MySQL 写入 `file_asset`、`elder_health_feedback`、`voice_command_log`。
- [x] 授权家属经 `http://localhost:3000` 分页读取；签名语音 URL 实际返回 HTTP 200。
- [x] 护理角色读取返回 403；自动化测试覆盖待确认绑定和他人文件拒绝。
- [x] 高严重度写入一条 `operation_log`，`health_archive.archive_version` 保持为 1。
