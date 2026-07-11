# 阶段 06-18 真实接口联调记录

日期：2026-07-10

## 覆盖内容

- 家属与长辈绑定：待确认绑定可直接更新；已生效绑定变更需长辈确认。
- 绑定范围：后端校验健康档案编辑、报告查看、报告确认、归档确认和订单改期权限。
- 基础档案：读取与保存返回完整档案内容。
- 服务地址：订单保存地址快照，删除地址不会影响历史订单展示。
- 订单、护理、报告：家属、护理员和管理员分别通过真实接口读取各自业务数据。

## 可复查结果

- `family_demo` 具有 `HEALTH_VIEW`、`REPORT_VIEW`、`ORDER_CREATE` 时可读取报告。
- 未授予 `HEALTH_EDIT`、`REPORT_CONFIRM`、`ARCHIVE_EDIT` 时，对应写入接口返回 HTTP 403。
- 删除历史订单曾使用的服务地址后，订单的 `address_id` 自动置空，服务地址快照仍可读取。
- 前端静态检查：`pnpm typecheck` 通过。

## 数据库迁移

- `db/migration/phase-06-binding-scope-approval.sql`
- `db/migration/phase-09-order-address-snapshot.sql`
