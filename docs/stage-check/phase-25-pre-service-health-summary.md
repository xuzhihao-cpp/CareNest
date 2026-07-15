# 阶段 25 验收记录：服务前健康摘要

## 交付范围

- 固定摘要接口：`GET /api/v1/nurse/orders/{orderId}/pre-service-health-summary`。
- 受保护病历预览：`GET /api/v1/nurse/orders/{orderId}/medical-files/{medicalFileId}/preview`。
- 护理人员仅可在订单和任务均为 `DISPATCHED`、`ACCEPTED`、`ON_THE_WAY` 且本人已派单时读取；`ADMIN` 可审阅。
- 摘要只读取正式健康档案、双方均审核通过的病历和 `WAIT_CONFIRM/CONFIRMED` 服务报告。
- 响应不返回档案版本、订单/长辈/文件/报告编号、审核意见、bucket 或 object key；档案版本只写入内部访问日志。

## 自动化验证

执行日期：2026-07-15。

- `mvn -pl backend-care-admin test`：38 项通过，0 失败。
- `mvn -pl backend-care-admin -Dtest=Phase25PreServiceSummaryApiTest test`：5 项通过，覆盖角色、任务窗口、跨长辈隔离、审核过滤、归档刷新、访问日志和病历预览。
- `pnpm test:stage25`：6 项通过。
- `pnpm typecheck`：通过。
- `pnpm build:h5`：通过。

## Docker 真实联调

使用 `phase25_order_001` 和真实 MySQL、MinIO、双后端及 Nginx 验证：

| 场景 | 结果 |
| --- | --- |
| `nurse_demo` 已派单护理读取摘要 | HTTP 200，风险/过敏/用药/慢病/病历/报告均来自真实接口 |
| `admin_demo` 审阅摘要 | HTTP 200 |
| `phase25_nurse_demo` 未派单护理 | HTTP 403 |
| `family_demo`、`elder_demo` | HTTP 403 |
| 未审核病历 | 未进入响应；仅返回 1 条 `APPROVED` 病历 |
| 报告过滤 | 仅返回 1 条 `CONFIRMED` 报告 |
| 受保护 PDF 预览 | HTTP 200，`application/pdf`，668 字节 |
| 访问审计 | `operation_log.operation_type=VIEW_PRE_SERVICE_HEALTH_SUMMARY`，护理与管理员记录均存在 |

已有数据卷执行 `db/migration/phase-25-pre-service-summary-contract.sql`，把旧 `CHECK_REPORT` 枚举迁移为冻结值 `EXAMINATION_REPORT`，并仅修复 bundled `demo/` 文件的历史 bucket 名称。空库继续使用最新 schema/seed，不自动重放 migration。

## 页面验收

- 护理端任务卡从真实 `ACCEPTED` 任务进入摘要，不手工输入订单编号。
- 页面顺序为重点风险、过敏信息、当前用药、慢病与照护要点、审核通过病历、近期服务摘要。
- 页面无编辑入口，未显示内部编号、档案版本、对象存储路径、审核意见、API 或 traceId。
- Playwright 使用 390 x 844 移动视口验证，控制台无错误。
- 截图：[护理端服务前健康摘要](assets/phase-25-nurse-summary.png)。
