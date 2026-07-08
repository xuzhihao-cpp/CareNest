# db/schema

存放数据库建表 SQL。阶段 3 开始在这里维护基础表结构，例如账号、角色、长辈、家属、护理人员、服务项目、订单、文件资产和操作日志。

规则：

- 建表字段必须先进入 `docs/dictionary/data-dictionary.md`。
- 表名使用 `snake_case`。
- SQL 文件按阶段或版本命名，例如 `phase-03-base-schema.sql`。