# 成员3阶段34-40验收记录

## 交付范围

本次仅推进成员3护理端与管理端后端：

- 阶段34：护理指标配置版本。
- 阶段35：订单指标清单快照。
- 阶段36：护理留档元数据提交与查询。
- 阶段37：管理端留档审核与审核历史。
- 阶段38：服务结束后的指标完成校验。
- 阶段39：未完成原因、附件证明和待审状态。
- 阶段40：豁免审核、指标结论和评分事实记录。

阶段32-33属于成员2提醒用户接口范围，本分支未实现。未修改 `db/`、`backend-user/`、`frontend/` 或 `mock/`，数据库交付继续由成员1分支负责。

## 契约对齐

- 接口来源：两份第2版增强开工文档阶段34-40。
- 数据来源：成员1 `phase-32-55/member1-db-complete` 冻结的表、字段、状态和权限码。
- API 文档：`docs/api/phase-34-40-care-metric-api.md`。
- 权限码：`CARE_METRIC_CONFIG_MANAGE`、`CARE_EVIDENCE_REVIEW`。
- 列表接口直接返回冻结记录数组，不扩展成员4未约定的分页字段。

## 关键规则证据

| 验收项 | 可复现证据 |
| --- | --- |
| 配置更新不影响历史订单 | `newConfigVersionDoesNotRewriteGeneratedChecklistSnapshot` 验证配置升至 v2 后订单仍保留 v1 权重和 itemId。 |
| 留档审核结果可见 | `approvedEvidencePassesMetricCheckAndWritesReviewHistory` 验证 `care_service_evidence` 与 `evidence_review_record`。 |
| 缺失项进入护理反馈 | 阶段38测试验证无已通过证据时写入 `MISSING` 和负 `score_delta`。 |
| 豁免通过不扣分 | `approvedExceptionProofRemovesDeductionInSameWorkflow` 验证 `EXEMPT_APPROVED` 与 0 分影响。 |
| 豁免驳回按快照扣分 | `rejectedExceptionProofKeepsConfiguredDeduction` 验证 `EXEMPT_REJECTED` 与 -10.00 分影响。 |
| 权限与数据归属 | `accessAndFrozenDecisionRulesAreEnforced` 验证非关联护理被拒绝、家属需 `REPORT_VIEW`、管理权限码不可绕过。 |
| 审核决策一致性 | `inconsistentProofReviewDecisionIsRejected` 验证通过不能选择扣分、驳回不能选择免扣。 |

## 并发与审计

- 配置保存锁定 `service_item`，串行生成 `config_version`。
- 清单生成、留档提交和指标校验锁定订单事实行，防止并发写出两份清单或交叉状态。
- 留档与证明审核使用带当前状态条件的更新，重复审核返回 409。
- 配置、清单、留档、审核、校验和证明流转均写 `operation_log`。

## 测试命令

```powershell
mvn -pl backend-care-admin -am test
mvn test
```

测试使用 H2 的 MySQL 兼容模式执行真实 JDBC SQL。Docker 未参与本阶段后端单元/事务测试；实际 MySQL 联调需在成员1阶段32-55数据库分支合并后执行。
