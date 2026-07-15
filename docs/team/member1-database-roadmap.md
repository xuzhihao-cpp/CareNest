# 成员1数据库与规范分阶段路线图

## 角色边界

成员1负责数据库、数据字典、状态字典、初始化 SQL、演示数据、统计 SQL 和字段变更审核。所有阶段必须遵守以下固定规则：

- API 字段使用 `camelCase`，数据库字段使用 `snake_case`。
- 共享字段、状态和枚举先进入 `docs/dictionary/data-dictionary.md`。
- 建表 SQL 放在 `db/schema/`，演示数据放在 `db/seed/`，字段变更放在 `db/migration/`。
- 关键状态流转必须写入 `operation_log` 或业务 `status_log`。
- 阶段验收证据保存到 `docs/stage-check/`。

## 阶段计划

| 阶段 | 模块 | 成员1交付 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| 2 | 登录与角色菜单 MVP | `sys_user`、`sys_role`、`user_role`、`login_session`、演示账号 | 阶段 1 | 数据库首版完成 |
| 3 | 权限拦截 MVP | `sys_permission`、`role_permission`、`operation_log` | 阶段 2 | 数据库首版完成 |
| 6 | 长辈/家属多对多绑定 MVP | `elder_family_binding`、`authorization_scope`、绑定状态字典 | 阶段 2、5 | 数据库首版完成 |
| 7 | 长辈基础档案 MVP | `elder_profile`、`elder_contact`、`health_archive_change_log` | 阶段 6 | 数据库首版完成 |
| 8 | 服务项目 MVP | `service_item`、服务项目演示数据 | 阶段 5 | 数据库首版完成 |
| 9 | 服务地址 MVP | `service_address`、默认地址约束规则 | 阶段 7 | 数据库首版完成 |
| 10 | 预约下单 MVP | `nursing_order`、`order_status_log`、初始状态日志 | 阶段 8、9 | 数据库首版完成 |
| 11 | 管理端订单列表 MVP | 订单查询索引、阶段 10 订单查询口径 | 阶段 10 | 数据库首版完成 |
| 12 | 派单 MVP | `nurse_task`、派单状态与订单状态日志 | 阶段 10 | 数据库首版完成 |
| 13 | 护理端任务工作台 MVP | `nurse_task` 查询索引和任务状态字典 | 阶段 12 | 数据库首版完成 |
| 14 | 护理服务记录 MVP | `care_service_record`、`vital_sign_record` | 阶段 13 | 数据库首版完成 |
| 15 | 服务报告 MVP | `service_report`、`service_report_item` | 阶段 14 | 数据库首版完成 |
| 16 | 长辈/家属确认 MVP | `care_report_ack`、`health_info_review_task` | 阶段 15 | 数据库首版完成 |
| 17 | 订单取消与改期 MVP | `nursing_order`、`order_status_log` 状态变更索引 | 阶段 10、12 | 数据库首版完成 |
| 18 | MVP 全流程联调 | 11-18 联调演示数据、`operation_log` 检查、验收证据 | 阶段 1-17 | 数据库首版完成 |
| 19 | 健康档案增强 | 慢病、过敏、风险、照护计划相关表 | 阶段 18 | 数据库首版完成 |
| 20 | 病历资料上传 | `file_asset`、`medical_file`、审核状态 | 阶段 19 | 数据库首版完成 |
| 24 | 健康信息审核归档 | 健康档案归档日志 | 阶段 21、23 | 数据库首版完成 |
| 26 | 护理注册与资质提交 | `nurse_profile`、`nurse_certificate`、文件关联 | 阶段 2 | 数据库首版完成 |
| 27 | 护理资质审核 | 资质审核状态、审核人、审核意见和申请历史约束 | 阶段 26 | 数据库首版完成 |
| 28 | 培训资格审核 | `nurse_training_record`、培训有效期和过期读取规则 | 阶段 27 | 数据库首版完成 |
| 29 | 护理推荐规则 | `nurse_service_skill`、`nurse_score`、`nurse_recommendation_log` | 阶段 8、26、28 | 数据库首版完成 |
| 30 | 护理推荐选择接入预约 | `nursing_order` 偏好护理字段和推荐原因快照 | 阶段 10、29 | 数据库首版完成 |
| 31 | 服务前注意事项 | `care_attention_notice`、`care_attention_ack`、幂等键 | 阶段 25 | 数据库首版完成 |
| 32 | 长辈提醒中心 | `reminder_task`、`reminder_record` | 阶段 19 | 未开始 |
| 33 | 提醒执行记录查询 | 提醒执行查询索引和记录状态 | 阶段 32 | 未开始 |
| 34 | 管理端护理指标配置 | `care_metric_config`、`care_metric_item`、`metric_score_rule` | 阶段 8 | 未开始 |
| 42 | AI 会话日志 | AI 会话和语音指令日志 | 阶段 41 | 未开始 |
| 47 | 护理评分模型 | `nurse_score`、评分变更日志 | 阶段 40、45、46 | 未开始 |
| 51 | 随访记录 | `follow_up_record`、提醒联动 | 阶段 24、43 | 未开始 |
| 52 | 基础数据看板 | 看板统计 SQL 和索引检查 | 阶段 18、43、51 | 未开始 |
| 53 | 质量数据看板 | 质量统计 SQL 和指标口径 | 阶段 38、40、47、52 | 未开始 |
| 54 | 全流程演示数据 | 一键初始化演示数据 | 阶段 1-53 | 未开始 |
| 55 | 全流程联调、测试与答辩彩排 | 数据验收、缺陷修复、答辩数据快照 | 阶段 54 | 未开始 |

## 当前批次

阶段 26-31 数据库基础已完成后，本批次提供护理准入、培训、推荐、偏好护理和服务前注意事项的 MySQL 结构、权限种子、演示数据、Redis 数据规范和验收说明，供成员2、成员3、成员4围绕真实接口继续联调。
