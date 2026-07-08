# db/migration

存放后续字段变更、表结构升级和数据修复脚本。

规则：

- 迁移脚本按时间或阶段顺序命名。
- 任何字段变更必须同步更新 `docs/dictionary/data-dictionary.md`。
- 影响接口字段时必须同步更新 `docs/api/` 和 `mock/`。