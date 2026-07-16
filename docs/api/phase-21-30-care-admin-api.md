# 成员3阶段21-30护理端与管理端后端接口

本文档只记录成员3主责接口。阶段19、20、22由其他成员负责，本模块只读取其产出的健康档案和病历数据。

## 通用规则

- 服务端口：`8082`
- 接口前缀：`/api/v1`
- 统一响应：`{code,message,data,traceId}`
- 分页结构：`{records,total,page,size}`
- 鉴权头：`Authorization: Bearer <token>`
- 固定错误码：`400/401/403/404/409/422/500`
- 审核状态：`PENDING/APPROVED/REJECTED/NEED_MORE`
- 审核、归档、资质、培训和偏好护理变更必须写 `operation_log` 或对应业务日志。

## 阶段21：管理端病历审核

| 方法 | 路径 | 请求/查询字段 | 响应 data | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/admin/medical-files` | `auditStatus,page,size` | `{records,total,page,size}` | `ADMIN/CUSTOMER_SERVICE` |
| GET | `/admin/medical-files/{fileId}` | - | 病历审核项 | `ADMIN/CUSTOMER_SERVICE` |
| POST | `/admin/medical-files/{fileId}/review` | `{auditStatus,reviewComment,extractToArchive,extractedItems}` | `{fileId,auditStatus,reviewedAt}` | `ADMIN/CUSTOMER_SERVICE` |

`REJECTED` 和 `NEED_MORE` 必须填写 `reviewComment`。只有 `APPROVED` 可设置 `extractToArchive=true`，且只生成健康审核任务，不直接覆盖档案。提取项格式为 `{fieldName,oldValue?,newValue}`，`fieldName` 必须来自健康档案白名单。

## 阶段23-25：健康建议、归档和服务前摘要

| 方法 | 路径 | 请求/查询字段 | 响应 data | 角色 |
| --- | --- | --- | --- | --- |
| POST | `/orders/{orderId}/health-update-suggestions` | `{fieldName,newValue,sourceType,sourceId,reason}` | `{suggestionId,status}` | 订单护理本人或 `ADMIN/CUSTOMER_SERVICE` |
| GET | `/admin/health-review-tasks` | `status,page,size` | `{records,total,page,size}` | `ADMIN/CUSTOMER_SERVICE` |
| GET | `/admin/health-review-tasks/{taskId}` | - | 审核任务详情 | `ADMIN/CUSTOMER_SERVICE` |
| POST | `/admin/health-review-tasks/{taskId}/archive` | `{decisions:[{sourceField,targetField,normalizedValue,decision,comment}]}` | `{taskId,status,archiveVersion}` | `ADMIN/CUSTOMER_SERVICE` |
| GET | `/nurse/orders/{orderId}/pre-service-health-summary` | - | `{elderProfile,riskTags,medications,diseases,allergies,approvedMedicalFiles,recentReports}` | 订单护理或 `ADMIN` |
| GET | `/nurse/orders/{orderId}/medical-files/{medicalFileId}/preview` | - | 已审核病历文件流 | 订单护理或 `ADMIN` |

健康建议仅允许 `SERVICE_RECORD/SERVICE_REPORT` 来源，来源记录必须真实属于当前订单，重复待审提交返回原建议。家属不能提交。

健康字段白名单为 `careSummary/riskTags/diseases/medications/allergies/carePlan`。归档状态为 `APPROVED/REJECTED/NEED_MORE`，版本号沿用 `health_archive.archive_version` 整数；只有批准项写正式档案并递增版本，驳回或要求补充不得修改档案。服务前摘要仅允许被派护理在订单和任务均处于 `DISPATCHED/ACCEPTED/ON_THE_WAY` 时查看；服务中及完成后不开放回看。摘要只返回已审核通过病历和状态为 `WAIT_CONFIRM/CONFIRMED` 的近期报告，并记录访问日志。响应和预览接口不返回对象存储路径、内部审核意见、档案版本或订单、长辈、文件、报告内部编号。

## 阶段26-28：护理准入和培训审核

| 方法 | 路径 | 请求/查询字段 | 响应 data | 角色 |
| --- | --- | --- | --- | --- |
| GET | `/dictionaries/nurseServiceSkill` | - | `{dictCode,items:[{value,label,sort}]}` | 已登录用户 |
| POST | `/nurse/qualification-applications` | `{realName,idNoMasked,certificateNo,certificateFileIds,serviceSkillCodes}` | 完整资质申请读模型 | `NURSE` + `NURSE_QUALIFICATION_SUBMIT` |
| GET | `/nurse/qualification-applications/current` | - | 完整资质申请读模型 | `NURSE` + `NURSE_QUALIFICATION_SUBMIT` |
| GET | `/admin/nurse-qualification-applications` | `auditStatus,page,size` | `{records,total,page,size}` | `ADMIN/CUSTOMER_SERVICE` |
| GET | `/admin/nurse-qualification-applications/{applicationId}/files/{fileId}/preview` | - | 授权文件流 | `ADMIN/CUSTOMER_SERVICE` + `NURSE_QUALIFICATION_REVIEW` |
| POST | `/admin/nurse-qualification-applications/{applicationId}/review` | `{auditStatus,reviewComment}` | `{nurseId,qualificationStatus}` | `ADMIN/CUSTOMER_SERVICE` |
| GET | `/nurse/training-status` | - | 完整培训状态读模型 | `NURSE` |
| GET | `/admin/nurses/{nurseId}/training-status` | - | 完整培训状态读模型 | `ADMIN/CUSTOMER_SERVICE` + `NURSE_TRAINING_REVIEW` |
| POST | `/admin/nurses/{nurseId}/training-review` | `{status,trainingBatch,expiredAt,remark}` | 完整培训状态读模型 | `ADMIN/CUSTOMER_SERVICE` + `NURSE_TRAINING_REVIEW` |

完整资质申请读模型为 `{applicationId,nurseId,nurseName,auditStatus,realName,idNoMasked,certificateNoMasked,certificateFiles,serviceSkillCodes,reviewComment,submittedAt,reviewedAt}`。完整培训状态读模型为 `{nurseId,nurseName,qualificationStatus,trainingStatus,trainingBatch,passedAt,expiredAt,remark}`。待审核或已通过资质不得重复提交正式申请；培训通过时 `expiredAt` 必须晚于当前时间。推荐接口只返回资质和培训均有效的护理。

## 阶段29-30：护理推荐和偏好选择

| 方法 | 路径 | 请求字段 | 响应 data | 角色 |
| --- | --- | --- | --- | --- |
| POST | `/orders/recommend-nurses` | `{elderId,serviceId,scheduledStart,addressId}` | `{nurses:[{nurseId,nurseName,score,matchedSkills,recommendReason,available}]}` | `FAMILY/ADMIN/NURSE` |
| GET | `/orders/{orderId}/recommendations` | - | 同上 | 可访问订单的 `FAMILY/ADMIN/NURSE` |
| PUT | `/family/orders/{orderId}/preferred-nurse` | `{preferredNurseId}` | `{orderId,preferredNurseId,recommendReason}` | `FAMILY` + `ACTIVE` 绑定 + `ORDER_CREATE` |
| GET | `/family/orders/{orderId}/recommendation-view` | - | `{orderId,preferredNurseId,recommendReason}` | `FAMILY` + `ACTIVE` 绑定 + `ORDER_CREATE` |

推荐结果必须包含后端生成的中文可解释理由并写入 `nurse_recommendation_log`。Redis 仅缓存相同条件的候选读模型，最长五分钟，MySQL 始终保存每次请求日志并作为事实源。订单推荐 GET 只读取创建订单时绑定的推荐日志，不在缺少日志时临时生成候选。偏好选择保存护理、推荐理由、推荐日志和操作人快照，订单继续保持 `WAIT_DISPATCH`，最终派单仍由管理端完成。

## 当前数据库依赖

- 阶段21-25已按远端 `main` 中阶段19、20、23、24真实表结构对齐。
- 阶段26-31生产表、权限、索引和演示数据位于 `db/schema/phase-26-31-nurse-admission-schema.sql` 与 `db/seed/phase-26-31-demo-data.sql`，必须通过空库初始化验收。
