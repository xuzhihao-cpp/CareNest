# 成员1阶段 2-9 数据库验收记录

## 阶段范围

本记录覆盖成员1数据库与规范侧首批交付：

- 阶段 2：登录与角色菜单 MVP
- 阶段 3：权限拦截 MVP
- 阶段 6：长辈/家属多对多绑定 MVP
- 阶段 7：长辈基础档案 MVP
- 阶段 8：服务项目 MVP
- 阶段 9：服务地址 MVP

## 交付文件

| 类型 | 路径 |
| --- | --- |
| 建表 SQL | `db/schema/phase-02-auth-schema.sql` |
| 建表 SQL | `db/schema/phase-03-permission-log-schema.sql` |
| 建表 SQL | `db/schema/phase-06-binding-schema.sql` |
| 建表 SQL | `db/schema/phase-07-elder-profile-schema.sql` |
| 建表 SQL | `db/schema/phase-08-service-item-schema.sql` |
| 建表 SQL | `db/schema/phase-09-service-address-schema.sql` |
| 演示数据 | `db/seed/phase-02-09-demo-data.sql` |
| 数据字典 | `docs/dictionary/data-dictionary.md` |
| 阶段路线图 | `docs/team/member1-database-roadmap.md` |
| 开发日志 | `docs/development-log.md` |

## 本地执行记录

执行环境：

- MySQL 容器：`carenest-mysql`
- 数据库：`smart_nursing`
- 字符集：`utf8mb4`

执行顺序：

```text
db/schema/phase-02-auth-schema.sql
db/schema/phase-03-permission-log-schema.sql
db/schema/phase-06-binding-schema.sql
db/schema/phase-07-elder-profile-schema.sql
db/schema/phase-08-service-item-schema.sql
db/schema/phase-09-service-address-schema.sql
db/seed/phase-02-09-demo-data.sql
```

Windows PowerShell 直接 `Get-Content | docker exec mysql` 可能导致中文被转成 `?`。包含中文演示数据的 SQL 建议按原始字节复制进容器后执行：

```text
docker cp db/seed/phase-02-09-demo-data.sql carenest-mysql:/tmp/phase-02-09-demo-data.sql
docker exec carenest-mysql sh -c "mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing < /tmp/phase-02-09-demo-data.sql"
```

## 验收查询结果

```text
Tables_in_smart_nursing
authorization_scope
elder_contact
elder_family_binding
elder_profile
health_archive_change_log
login_session
operation_log
role_permission
service_address
service_item
sys_permission
sys_role
sys_user
user_role
```

```text
table_name              rows_count
sys_user                5
sys_role                5
sys_permission          5
elder_profile           1
elder_family_binding    1
service_item            2
service_address         1
operation_log           1
```

中文演示数据 UTF-8 校验：

```text
username     display_name
admin001     平台管理员
cs001        客服一号
elder001     张爷爷
family001    张小明
nurse001     李护士

service_id   service_name
service_001  基础上门护理
service_002  康复陪护
```

## 验收结论

- [x] 阶段 2-9 数据库基础表已创建。
- [x] 演示账号、角色、权限、长辈档案、绑定关系、服务项目、服务地址已初始化。
- [x] 演示账号密码使用 bcrypt 哈希，不保存明文或 noop 密码。
- [x] 阶段 2-9 新增字段已同步到 `docs/dictionary/data-dictionary.md`。
- [x] 状态值使用数据字典维护。
- [x] 建表与 seed SQL 可重复执行。

## 后续事项

- 阶段 10-18 需要继续补齐订单、派单、护理任务、服务记录、服务报告、报告确认和订单状态日志。
- 与成员2、成员3、成员4联调前，需要确认后端 DTO 和前端类型字段不偏离数据字典。
