# 成员1阶段 6-10 数据库验收记录

## 阶段范围

本记录覆盖阶段 5 合并后继续推进的成员1数据库交付：

- 阶段 6：长辈/家属多对多绑定 MVP
- 阶段 7：长辈基础档案 MVP
- 阶段 8：服务项目 MVP
- 阶段 9：服务地址 MVP
- 阶段 10：预约下单 MVP

## 交付文件

| 类型 | 路径 |
| --- | --- |
| 建表 SQL | `db/schema/phase-06-binding-schema.sql` |
| 建表 SQL | `db/schema/phase-07-elder-profile-schema.sql` |
| 建表 SQL | `db/schema/phase-08-service-item-schema.sql` |
| 建表 SQL | `db/schema/phase-09-service-address-schema.sql` |
| 建表 SQL | `db/schema/phase-10-order-schema.sql` |
| 演示数据 | `db/seed/phase-06-09-demo-data.sql` |
| 演示数据 | `db/seed/phase-10-demo-data.sql` |
| 数据字典 | `docs/dictionary/data-dictionary.md` |
| 阶段路线图 | `docs/team/member1-database-roadmap.md` |
| 开发日志 | `docs/development-log.md` |

## 执行顺序

```text
db/schema/phase-06-binding-schema.sql
db/schema/phase-07-elder-profile-schema.sql
db/schema/phase-08-service-item-schema.sql
db/schema/phase-09-service-address-schema.sql
db/seed/phase-06-09-demo-data.sql
db/schema/phase-10-order-schema.sql
db/seed/phase-10-demo-data.sql
```

包含中文演示数据的 SQL 建议按原始字节复制进容器后执行：

```text
docker cp db/seed/phase-10-demo-data.sql carenest-mysql:/tmp/phase-10-demo-data.sql
docker exec carenest-mysql sh -c "mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing < /tmp/phase-10-demo-data.sql"
```

## 验收查询结果

```text
table_name                rows_count
authorization_scope       6
elder_profile             1
elder_contact             1
elder_family_binding      1
service_item              2
service_address           1
nursing_order             1
order_status_log          1
```

## 认证联动验收

第一次合并后组长明确父项目依赖和 Docker 环境已就位，本阶段同步完成认证实现落库改造：

- `DemoAuthRepository` 已由内存集合改为 MyBatis-Plus Mapper 操作 MySQL。
- 登录密码已由明文比较改为 BCrypt 校验。
- 登录 token 已由 UUID 字符串改为 JWT，`login_session` 仅保存 token hash、过期时间和撤销时间。
- Controller 路径、请求 DTO 和响应 DTO 保持兼容。
- 阶段 2-3 演示账号与阶段 6-10 业务演示数据统一使用 `elder-001`、`family-001` 等用户 ID。

已执行验证：

```text
mvn -pl backend-user -DskipTests compile
mvn -pl backend-user test
```

测试结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

## 验收结论

- [x] 阶段 6-10 数据库基础表已创建。
- [x] 授权范围、长辈档案、绑定关系、服务项目、服务地址和预约订单演示数据已初始化。
- [x] 阶段 10 演示订单状态为 `WAIT_DISPATCH`，并写入 `order_status_log`。
- [x] 阶段 6-10 新增字段已同步到 `docs/dictionary/data-dictionary.md`。
- [x] 建表与 seed SQL 可重复执行。
- [x] 认证、权限接口已在 MySQL + BCrypt + JWT 实现下通过后端测试。

## 后续事项

- 阶段 11-15 继续补齐管理端订单列表、派单、护理任务、服务记录和服务报告。
