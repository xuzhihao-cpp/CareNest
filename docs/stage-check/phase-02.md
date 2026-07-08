# Phase 02 验收记录：数据字典与状态字典初版

## 阶段目标

产出数据字典与状态字典初版，作为数据库、后端 DTO、前端 mock 的唯一字段来源。

## 验收清单

- [ ] `docs/dictionary/data-dictionary.md` 包含字段字典表。
- [ ] `docs/dictionary/data-dictionary.md` 包含状态和枚举字典表。
- [ ] 核心字典包含 `roleCode`、`orderStatus`、`auditStatus`、`bindingStatus`、`reminderStatus`、`metricStatus`、`ticketStatus`、`complaintStatus`、`appealStatus`、`articleStatus`。
- [ ] `roleCode` 包含 `ELDER`、`FAMILY`、`NURSE`、`ADMIN`、`CUSTOMER_SERVICE`。
- [ ] `docs/api/global-contract.md` 冻结统一响应、分页、认证、时间和上传规则。
- [ ] `docs/api/phase-01-02-api.md` 记录阶段 1-2 接口契约。
- [ ] `mock/phase-01/` 和 `mock/phase-02/` 中的 JSON 字段与接口文档一致。

## 证据记录

| 证据类型 | 路径或说明 | 验收人 | 结果 |
| --- | --- | --- | --- |
| 数据字典 | `docs/dictionary/data-dictionary.md` |  |  |
| 接口契约 | `docs/api/global-contract.md`, `docs/api/phase-01-02-api.md` |  |  |
| Mock JSON | `mock/phase-01/`, `mock/phase-02/` |  |  |

## 备注

阶段 2 使用 Markdown 作为唯一人工维护源。若后续需要 Excel，可从 Markdown 派生，不反向维护。