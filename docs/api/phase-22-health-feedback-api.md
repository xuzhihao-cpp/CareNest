# 阶段 22 健康反馈接口

## POST /api/v1/elder/health-feedback

仅长辈本人可提交。后端从 Bearer Token 解析用户并关联 `elder_profile`，请求不得指定 `elderId`。

```json
{"feedbackType":"PAIN","severity":"HIGH","content":"起身时疼痛","inputType":"TEXT","fileId":null}
```

- `feedbackType`: `PAIN | DIZZINESS | SLEEP | DIET | MENTAL_STATE`
- `severity`: `LOW | MEDIUM | HIGH`
- `inputType`: `BUTTON | TEXT | VOICE`
- `content`: 最多 512 字。
- `VOICE` 必须提供当前长辈本人上传的音频 `fileId`，最大 12 MiB；其他输入方式禁止携带文件。
- `HIGH` 仅写入优先级审计信号，不修改健康档案、不做诊断，也不虚构通知结果。

成功返回 `feedbackId`、`createdAt` 和 `aiAdvice`。`aiAdvice` 由现有 AI
安全链路根据反馈类型、程度和补充说明生成，只提供日常照护建议，不诊断、不调整用药；
高风险反馈会明确建议联系家属、专业医护人员或急救服务。

## GET /api/v1/family/elders/{elderId}/health-feedback

要求 `FAMILY + ACTIVE + HEALTH_VIEW`。支持 `page`、`size`、`feedbackType`、`severity`、`dateFrom`、`dateTo`；`size` 范围为 1-50。结果按严重程度 `HIGH > MEDIUM > LOW`，同级按创建时间倒序。

返回统一分页结构：

```json
{"records":[],"total":0,"page":1,"size":20}
```

语音记录返回 10 分钟有效的 `voiceUrl`。对象 key、MinIO 密钥和其他长辈数据不返回。

## 通用文件上传补充

`POST /api/v1/files` 继续校验文件签名和声明 MIME，并增加 MP3、M4A、WAV、AAC、WEBM、OGG。音频资产不能登记为阶段 20 医疗资料。
