# CareNest 协作规范

## 基本原则

CareNest 采用多人并行开发。所有成员必须先遵守数据字典、接口契约和阶段验收规则，再开始写业务代码。

## 开工流程

1. 从 GitHub Issue 领取阶段任务。
2. 基于 `main` 创建阶段分支。
3. 按阶段文档修改对应目录。
4. 若改动字段、状态或接口，先更新数据字典和接口文档。
5. 提交 PR，并填写 PR 模板里的检查项。
6. 至少 1 人 review 后合并。

## 分支命名

使用 `phase-编号/英文短名`：

- `phase-01/project-structure`
- `phase-02/data-dictionary`
- `phase-09/family-bindings`
- `phase-01-02/github-kickoff`

## 提交信息

推荐格式：

```text
docs: update data dictionary
feat: add family binding endpoint
fix: correct order status mock
chore: update github templates
```

阶段 1-2 只使用 `docs:` 或 `chore:`，不使用 `feat:`。

## 字段和状态变更规则

以下改动必须同步更新 `docs/dictionary/data-dictionary.md`：

- 新增或修改 API 字段
- 新增或修改数据库列
- 新增或修改状态枚举
- 修改 ID 命名
- 修改分页、上传、认证、错误码规则

禁止在前端 mock、后端 DTO、数据库 SQL 中临时创造同义字段。

## PR 审查重点

- 是否影响数据字典。
- 是否影响接口契约。
- mock JSON 是否与接口文档一致。
- 是否补充阶段验收证据。
- 是否影响其他开发泳道。

## 目录责任

| 路径 | 责任 |
| --- | --- |
| `docs/dictionary/data-dictionary.md` | 数据负责人维护，所有成员引用。 |
| `docs/api/` | 后端负责人维护，前端和 mock 必须遵守。 |
| `db/` | 数据负责人维护，阶段 3 后写入 SQL 和 ER 图。 |
| `frontend/` | 前端负责人维护。 |
| `mock/` | 前端负责人维护，但字段必须来自接口文档和数据字典。 |
| `.github/` | 项目负责人维护。 |

## 验收证据

每个阶段必须在 `docs/stage-check/` 记录证据。没有验收证据的阶段不视为完成。