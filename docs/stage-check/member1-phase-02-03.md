# 成员1阶段 2-3 数据库验收记录

## 阶段范围

本记录覆盖阶段 5 合并前可进入主线的成员1数据库交付：

- 阶段 2：登录与角色菜单 MVP
- 阶段 3：权限拦截 MVP

阶段 6-9 依赖阶段 5 或更后置阶段，已从本次可合并范围移除。

## 交付文件

| 类型 | 路径 |
| --- | --- |
| 建表 SQL | `db/schema/phase-02-auth-schema.sql` |
| 建表 SQL | `db/schema/phase-03-permission-log-schema.sql` |
| 演示数据 | `db/seed/phase-02-03-demo-data.sql` |
| 数据字典 | `docs/dictionary/data-dictionary.md` |
| 阶段路线图 | `docs/team/member1-database-roadmap.md` |
| 开发日志 | `docs/development-log.md` |

## 执行顺序

```text
db/schema/phase-02-auth-schema.sql
db/schema/phase-03-permission-log-schema.sql
db/seed/phase-02-03-demo-data.sql
```

包含中文演示数据的 SQL 建议按原始字节复制进容器后执行：

```text
docker cp db/seed/phase-02-03-demo-data.sql carenest-mysql:/tmp/phase-02-03-demo-data.sql
docker exec carenest-mysql sh -c "mysql --default-character-set=utf8mb4 -usmart_nursing -psmart_nursing123 smart_nursing < /tmp/phase-02-03-demo-data.sql"
```

## 验收查询结果

```text
table_name        rows_count
sys_user          5
sys_role          5
sys_permission    5
user_role         5
role_permission   14
operation_log     1
```

## 验收结论

- [x] 阶段 2-3 数据库基础表已创建。
- [x] 演示账号、角色、权限和操作日志已初始化。
- [x] 演示账号密码使用 bcrypt 哈希，不保存明文或 noop 密码。
- [x] 阶段 2-3 新增字段已同步到 `docs/dictionary/data-dictionary.md`。
- [x] 建表与 seed SQL 可重复执行。

## 后续事项

- 阶段 5 合并后，再继续合并阶段 6-9 的绑定、长辈档案、服务项目和服务地址数据库交付。
