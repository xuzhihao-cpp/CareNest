# 成员1阶段 11-18 数据库验收记录

## 阶段范围

本记录覆盖阶段 11-18 的成员1数据库与规范交付：

- 阶段 11：管理端订单列表 MVP
- 阶段 12：派单 MVP
- 阶段 13：护理端任务工作台 MVP
- 阶段 14：护理服务记录 MVP
- 阶段 15：服务报告 MVP
- 阶段 16：长辈/家属确认 MVP
- 阶段 17：订单取消与改期 MVP
- 阶段 18：MVP 全流程联调

## 交付文件

| 类型 | 路径 |
| --- | --- |
| 建表 SQL | `db/schema/phase-11-admin-order-query-schema.sql` |
| 建表 SQL | `db/schema/phase-12-13-nurse-task-schema.sql` |
| 建表 SQL | `db/schema/phase-14-service-record-schema.sql` |
| 建表 SQL | `db/schema/phase-15-service-report-schema.sql` |
| 建表 SQL | `db/schema/phase-16-report-ack-schema.sql` |
| 建表 SQL | `db/schema/phase-17-order-change-schema.sql` |
| 演示数据 | `db/seed/phase-11-18-demo-data.sql` |
| 数据字典 | `docs/dictionary/data-dictionary.md` |
| 阶段路线图 | `docs/team/member1-database-roadmap.md` |
| 开发日志 | `docs/development-log.md` |

## 执行顺序

```text
db/schema/phase-11-admin-order-query-schema.sql
db/schema/phase-12-13-nurse-task-schema.sql
db/schema/phase-14-service-record-schema.sql
db/schema/phase-15-service-report-schema.sql
db/schema/phase-16-report-ack-schema.sql
db/schema/phase-17-order-change-schema.sql
db/seed/phase-11-18-demo-data.sql
```

执行前需已完成阶段 2-10 的 schema 和 seed。

## 验收查询结果

```text
table_name                rows_count
nurse_task                1
care_service_record       1
vital_sign_record         1
service_report            1
service_report_item       3
care_report_ack           1
health_info_review_task   1
```

关键状态链路：

```text
order_001 WAIT_DISPATCH -> DISPATCHED -> ACCEPTED -> ON_THE_WAY
order_001 ON_THE_WAY -> SERVING -> WAIT_REPORT -> WAIT_CONFIRM -> COMPLETED
task_001  COMPLETED
report_001 CONFIRMED
```

## 验收结论

- [x] 阶段 11 订单列表查询所需组合索引已补齐。
- [x] 阶段 12-13 派单与护理任务表 `nurse_task` 已创建。
- [x] 阶段 14 服务记录和生命体征表已创建。
- [x] 阶段 15 服务报告和报告明细表已创建。
- [x] 阶段 16 报告确认和健康信息审核任务表已创建。
- [x] 阶段 17 订单状态日志查询索引已补齐，未额外发明未列出的业务表。
- [x] 阶段 18 演示数据可形成从下单到确认完成的完整链路。
- [x] 新增字段和状态枚举已同步到 `docs/dictionary/data-dictionary.md`。
- [x] 建表与 seed SQL 可重复执行。

## 后续事项

- 成员2、成员3、成员4继续按接口文档接入真实接口和页面。
- 阶段 19 以后健康档案增强、病历上传、审核归档等内容不在本次数据库交付范围内。
